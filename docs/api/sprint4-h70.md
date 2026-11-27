# H70 — Restaurar una versión anterior

Responsable del backend: Leonel (script y Django). Web: Yajaira (lista de versiones e historial de
restauraciones en el panel de respaldos, junto con H58 y H59). Backlog 14.6. Prioridad «Si sobra».

H58 recupera **el último** respaldo después de una falla. H70 sirve para **volver a un momento
anterior** a propósito (por ejemplo, después de borrar datos por error): elegir una versión, ver qué
cambiaría y aplicarla sin perder la posibilidad de deshacerlo.

## Qué es una versión

Una **versión** es el par de respaldos de una misma ejecución del servicio: el de la base (`BASE`, H38)
y el de fotos (`FOTOS`, H59), los dos `COMPLETADO` y separados por menos de 1 hora. Se identifica por
la marca de tiempo UTC del archivo de la base, por ejemplo `20261120T020849Z`.

Una versión está **disponible** mientras sus dos archivos existan. La retención (7 días por defecto)
borra los archivos viejos; desde H70 el servicio de respaldo marca esas filas con `eliminado_en`, en
vez de dejarlas como si todavía se pudieran usar.

## Rutas (Django, solo `ADMIN`, solo lectura)

La restauración **no** se hace desde la web: reemplazar la base exige detener la API y acceso a los
volúmenes, y el panel no debe tener ese poder. La web muestra las versiones, el comando y el historial.

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/respaldos/versiones/` | Versiones, la más reciente primero (paginada, 10 por página) |
| `GET` | `/api/admin/restauraciones/` | Historial de restauraciones y ensayos, el más reciente primero |
| `GET` | `/api/admin/restauraciones/{id}/` | Detalle |

`/api/admin/respaldos/` y `/{id}/` agregan `eliminadoEn` (fecha UTC o `null`).

### `GET /api/admin/respaldos/versiones/`

```json
{
  "count": 2,
  "next": null,
  "previous": null,
  "results": [
    {
      "version": "20261120T020849Z",
      "fecha": "2026-11-20T02:08:49Z",
      "disponible": true,
      "motivo": null,
      "base":  { "id": 41, "nombreArchivo": "foundia_objetos_perdidos_db_20261120T020849Z.sql.gz", "tamanoBytes": 18324, "eliminadoEn": null },
      "fotos": { "id": 42, "nombreArchivo": "foundia_fotos_20261120T020849Z.tar.gz", "tamanoBytes": 412233, "eliminadoEn": null }
    },
    {
      "version": "20261112T030000Z",
      "fecha": "2026-11-12T03:00:00Z",
      "disponible": false,
      "motivo": "Los archivos se borraron por la retención.",
      "base":  { "id": 20, "...": "..." },
      "fotos": null
    }
  ]
}
```

| `motivo` (cuando `disponible` es `false`) |
| --- |
| `Los archivos se borraron por la retención.` |
| `No hay respaldo de fotos de la misma ejecución.` |

Solo se listan los respaldos de la base `COMPLETADO`. Los `FALLIDO` o `EN_PROCESO` no son versiones.

### `GET /api/admin/restauraciones/`

```json
{
  "id": 3,
  "version": "20261120T020849Z",
  "modo": "APLICADA",
  "estado": "COMPLETADA",
  "respaldoBase": "foundia_objetos_perdidos_db_20261120T020849Z.sql.gz",
  "respaldoFotos": "foundia_fotos_20261120T020849Z.tar.gz",
  "respaldoSeguridad": "20261122T231502Z",
  "responsable": "Leonel",
  "iniciadoEn": "2026-11-22T23:15:00Z",
  "finalizadoEn": "2026-11-22T23:15:09Z",
  "mensaje": "objetos 21 -> 15; fotos 12 -> 8; usuarios 5 -> 4; ..."
}
```

| Campo | Valores |
| --- | --- |
| `modo` | `ENSAYO` (solo se probó en una base aislada) o `APLICADA` (se reemplazó la base y las fotos) |
| `estado` | `EN_PROCESO`, `COMPLETADA` o `FALLIDA` |
| `respaldoSeguridad` | Versión creada **justo antes** de aplicar, para deshacerla. `null` en un ensayo |

Errores: `401` sin sesión, `403` si no es `ADMIN`, `404` si no existe, `405` para cualquier escritura.

## Script: `scripts/restaurar_version.sh`

Corre en un contenedor con el cliente de MySQL (la imagen del servicio de respaldo), igual que H58.

1. Sin `VERSION`: lista las versiones que hay en `RESPALDO_DIR` y termina.
2. Con `VERSION=<marca>`: busca el par base + fotos. Sin par, se niega.
3. **Ensayo (por defecto):** restaura la versión en una base aislada (`objetos_perdidos_version`) y en una
   carpeta temporal, verificando las huellas, y muestra qué cambiaría comparando con la base actual:
   usuarios, publicaciones, fotos, reclamos, custodias y mensajes. No toca la base ni las fotos en uso.
4. Comprueba que la versión tenga **el mismo esquema** (tablas y columnas) que la base actual. Si es de
   antes de una migración, se niega a aplicarla: la API actual fallaría con un esquema viejo.
5. **Aplicar** (`RESTAURACION_APLICAR=si` y `RESTAURACION_RESPONSABLE=<nombre>`):
   1. Hace un **respaldo de seguridad** del estado actual (base y fotos), que queda como una versión
      más. Para deshacer la restauración, se restaura esa versión.
   2. Reemplaza la base de producción con la versión.
   3. Conserva el historial: las tablas `respaldos` y `restauraciones` quedan como estaban antes de
      restaurar (si no, el historial volvería al pasado y los archivos nuevos quedarían sin registro).
   4. Reemplaza las fotos del volumen en uso con las de la versión.
   5. **Cierra todas las sesiones** subiendo `sesion_version` por encima de la que tenía cada cuenta antes,
      porque una versión vieja traería de vuelta sesiones ya cerradas o revocadas.
6. Registra cada ensayo y cada aplicación en `restauraciones`.

Antes de aplicar, detener la API, el panel y el servicio de respaldo, como en H58.

### Cómo ejecutarlo (Docker Compose)

Después de traer H70, reconstruir la imagen del servicio de respaldo (`docker compose build respaldo`) para
que la retención marque `eliminado_en`. En Git Bash, anteponer `MSYS_NO_PATHCONV=1`:

```bash
# Ver las versiones y ensayar una (no cambia nada en uso)
docker run --rm --network proyectofoundia_default --entrypoint bash   -v proyectofoundia_respaldos_data:/respaldos   -v proyectofoundia_uploads_data:/fotos-en-uso   -v "$PWD/scripts:/s:ro"   -e DB_HOST=mysql -e DB_PASSWORD=root -e FOTOS_EN_USO=/fotos-en-uso   -e VERSION=20261120T020849Z   proyectofoundia-respaldo /s/restaurar_version.sh

# Aplicarla: detener antes la API, el panel y el respaldo, y agregar al comando anterior
#   -e RESTAURACION_APLICAR=si -e RESTAURACION_RESPONSABLE="Nombre Apellido"
docker compose stop api admin-api respaldo frontend-usuario frontend-admin
# ... ejecutar ...
docker compose up -d
```

Sin `-e VERSION=...` solo lista las versiones. Los nombres de red, volúmenes e imagen dependen del proyecto
de Compose; comprobarlos con `docker volume ls` y `docker network ls`.

## Límites (declarados)

- **Se pierde todo lo ocurrido después de la versión** (publicaciones, reclamos, mensajes, cuentas nuevas).
  El ensayo lo muestra antes de aplicar y el respaldo de seguridad permite deshacerlo.
- Solo hay versiones de los últimos `RESPALDO_RETENCION_DIAS` (7 por defecto) y una por ejecución del
  servicio (cada 24 h por defecto). No es posible volver a un minuto cualquiera.
- No se restaura una versión con un esquema distinto (por ejemplo, de antes de una migración). Habría que
  restaurarla en una base aislada y aplicarle las migraciones a mano.
- Las sesiones de Firebase (H72) no se revocan desde aquí; las de FoundIA sí. Después de aplicar, todas las
  personas deben volver a iniciar sesión.
- Los avisos push ya enviados y los correos no se deshacen.
- Mientras se aplica (unos segundos con pocos datos) la API debe estar detenida.

## Base de datos

`database/migracion_h70_restauraciones.sql` (idempotente, también en `script.sql`):

```sql
ALTER TABLE respaldos ADD COLUMN eliminado_en DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS restauraciones (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version            VARCHAR(20)   NOT NULL,
    modo               VARCHAR(10)   NOT NULL,   -- ENSAYO | APLICADA
    estado             VARCHAR(12)   NOT NULL DEFAULT 'EN_PROCESO',  -- EN_PROCESO | COMPLETADA | FALLIDA
    respaldo_base      VARCHAR(255)  NOT NULL,
    respaldo_fotos     VARCHAR(255)  NOT NULL,
    respaldo_seguridad VARCHAR(20)   NULL,
    responsable        VARCHAR(120)  NULL,
    iniciado_en        DATETIME(6)   NOT NULL,
    finalizado_en      DATETIME(6)   NULL,
    mensaje            VARCHAR(1000) NULL
);
```

## Lo que necesita la web (Yajaira)

- En el panel de respaldos, una tabla con `GET /api/admin/respaldos/versiones/`: fecha (en hora de Lima),
  tamaño, `disponible` y `motivo`.
- El comando para ensayar una versión, con su `version` ya puesta, para copiarlo. La web no restaura.
- El historial con `GET /api/admin/restauraciones/`: modo, estado, responsable, fechas y `mensaje`.

## Pruebas que se deben ejecutar y registrar

- Versiones: par completo disponible; sin fotos de la misma ejecución; archivos borrados por retención;
  un respaldo `FALLIDO` no aparece; orden y paginación.
- Restauraciones: lista y detalle; escrituras `405`; sin sesión `401`; con `BIBLIOTECA` `403`.
- Con MySQL real:
  - Ensayo: no cambia la base ni las fotos en uso y muestra la diferencia.
  - Versión inexistente, sin par o con huella alterada: se niega sin tocar nada.
  - Versión con un esquema distinto: se niega a aplicar.
  - Aplicar: vuelve a los datos de esa versión (conteos y fotos idénticos), deja el respaldo de
    seguridad, conserva el historial de `respaldos` y `restauraciones` y cierra las sesiones (un JWT
    anterior responde `401`).
  - Deshacer: restaurar el respaldo de seguridad devuelve los datos de antes.
  - La retención marca `eliminado_en` y la versión aparece como no disponible.

## Verificación del 22/11/2026

- Django: 13 pruebas nuevas en `test_sprint4_h70.py` (par completo, fotos más cercanas, sin par, retención,
  `FALLIDO`/`EN_PROCESO` fuera, fotos `FALLIDO` no cuentan, orden y paginación, `eliminadoEn` en la lista,
  historial y detalle, escrituras `405`, `401` y `403`). Suite completa: **157 pruebas, OK**.
- MySQL 8.0 de QA (proyecto aislado `foundia-qa`, datos de demostración):

| Caso | Resultado |
| --- | --- |
| Migración | `migracion_h70_restauraciones.sql` dos veces sin error |
| Versión A | Respaldo `20261123T011617Z`: 18 publicaciones, 8 fotos registradas, 6 archivos |
| Cambio posterior | Una publicación nueva por la API (`201`) y una foto más: 19 publicaciones, 7 archivos |
| Listar | Muestra las versiones y marca «SIN PAR» la que no tiene fotos de la misma ejecución |
| Ensayo de A | 6 s. Muestra `objetos 19 -> 18`, `archivos de fotos 7 -> 6` y 1 publicación que se perdería. La base y las fotos en uso no cambiaron; la base aislada se borró al terminar |
| Rechazos | Versión inexistente, marca inválida, sin par de fotos y huella alterada: se niegan sin tocar nada (la de huella queda `FALLIDA` con el motivo) |
| Esquema anterior | Una versión sin `respaldos.eliminado_en`: el ensayo lo muestra y aplicar se niega (`La base en uso no cambió`), sin dejar la base aislada |
| Sin responsable | Aplicar sin `RESTAURACION_RESPONSABLE` se niega |
| **Aplicar A** | **14 s**, con la API detenida. Respaldo de seguridad `20261123T012024Z`. La base quedó igual a A (solo faltó la publicación posterior) y los 6 archivos con el mismo SHA-256 que en A |
| Historial | Las 4 filas de `respaldos` y las 6 de `restauraciones` (ensayos y fallos incluidos) se conservaron |
| Sesiones | `sesion_version` de 0, 1, 0, 0 a 1, 2, 1, 1. El JWT de antes respondió **`401`** en `/api/perfil`; un inicio de sesión nuevo, `200` |
| **Deshacer** | Aplicar la versión de seguridad devolvió la base y los 7 archivos **idénticos** a antes de restaurar (13 s). `sesion_version` subió a 2, 3, 2, 2: no se reutilizó ninguna versión de sesión |
| Retención | Con archivos de 10 días y `RESPALDO_RETENCION_DIAS=1`, borró los de A y marcó sus 2 filas con `eliminado_en` |
| Panel (Django real) | `versiones/` `200`: 4 versiones, A con `disponible: false` y «Los archivos se borraron por la retención.»; `restauraciones/` `200` con 7 filas y `respaldoSeguridad`; `POST` `405`; `eliminadoEn` en `/api/admin/respaldos/` |

- Encontrado y corregido durante la prueba: la imagen de respaldo no trae `diff`, y la primera versión del
  script daba siempre «esquema distinto». Ahora compara con `sort` y `comm`, que sí están en la imagen.
- **Falta en la web (Yajaira):** mostrar las versiones, el comando y el historial (ver «Lo que necesita la web»).

## Restaurar desde el panel (23/11/2026)

El Sprint 4 pide restaurar una versión «elegida desde el listado de respaldos, con confirmación y respaldo
previo automático», con `403` para quien no es administrador. Hasta ahora solo se podía por script. El equipo
eligió hacerlo con **modo mantenimiento**.

### Cómo funciona

1. **Panel (Django):** `POST /api/admin/respaldos/versiones/{version}/restaurar/`, solo `ADMIN`, con **doble
   confirmación**: `{"confirmacionVersion": "<la misma versión>", "confirmacionTexto": "RESTAURAR"}`. La versión
   tiene que estar disponible (base y fotos de la misma ejecución, sin borrar por la retención). Responde **`202`**
   con la restauración `SOLICITADA` (estado nuevo).
2. **Servicio de respaldo:** el panel no tiene acceso a los respaldos ni a las herramientas de MySQL. El servicio
   revisa cada **15 segundos** (`RESTAURACION_REVISAR_SEGUNDOS`) si hay una solicitud y la ejecuta con
   `restaurar_version.sh`, con todas sus guardas: huellas, mismo esquema, **respaldo de seguridad previo**,
   historial conservado y sesiones cerradas. La misma fila pasa a `EN_PROCESO` y luego a `COMPLETADA`.
3. **Modo mantenimiento:** mientras haya una restauración `SOLICITADA` o `EN_PROCESO`, **Spring y Django
   responden `503`** a las escrituras (`POST`, `PUT`, `PATCH`, `DELETE`) con el mensaje «FoundIA está restaurando
   una versión anterior de los datos…» y `Retry-After: 60`. Las lecturas y el inicio de sesión siguen. Si la base
   no responde justo mientras se reemplaza, también `503`. El chequeo se guarda 2 segundos.
4. **Si algo falla,** el servicio deja la solicitud `FALLIDA` con el motivo (por ejemplo, «No hay un respaldo de
   la base con la versión …») y el mantenimiento se apaga solo. La base y las fotos en uso no cambian.

| Código | Caso |
| --- | --- |
| `202` | Solicitada |
| `400` | Confirmación de la versión o del texto que no coincide |
| `401` / `403` | Sin sesión / no es `ADMIN` |
| `404` | La versión no existe |
| `409` | La versión no se puede restaurar (con el motivo), o ya hay una restauración en curso |
| `503` | Modo mantenimiento (otra escritura mientras se restaura) |

- Al terminar se cierran **todas** las sesiones, también la del administrador: el panel debe volver a pedir
  el inicio de sesión.
- Para **deshacer**, restaurar desde el panel la versión de seguridad (`respaldoSeguridad`).
- Si la versión trae de vuelta cuentas eliminadas (H34), el panel tiene
  `POST /api/admin/solicitudes-eliminacion/reaplicar/` → `{"cuentasAnonimizadasDeNuevo": n}`.
- **Cambio en Docker:** el servicio de respaldo monta las fotos **con escritura** (para restaurarlas) y su imagen
  incluye los scripts de restauración. Respaldar sigue sin modificar las fotos. Hay que reconstruirlo:
  `docker compose up -d --build respaldo`.
- Base de datos: `database/migracion_h70b_restaurar_desde_panel.sql` (estado `SOLICITADA`).

### Lo que necesita la web (Yajaira)

- En la lista de versiones, el botón «Restaurar» con **doble confirmación**: escribir la versión y la palabra
  `RESTAURAR`, avisando que se perderá lo posterior, que se cerrarán todas las sesiones y que se puede deshacer
  con la versión de seguridad.
- Mostrar el avance con `GET /api/admin/restauraciones/{id}/` y, ante un `401` al terminar, volver al inicio de
  sesión. Ante `503`, mostrar el mensaje de mantenimiento.

### Verificación del 23/11/2026 (desde el panel)

- Django: 8 pruebas nuevas en `test_sprint4_h70_panel.py` (solicitada con responsable y archivos; doble
  confirmación; versión inexistente `404` y no disponible `409`; una a la vez; `401` y `403`; mantenimiento en el
  panel con `Retry-After` y lecturas e inicio de sesión que siguen; un ensayo no activa el mantenimiento;
  reaplicar eliminaciones desde el panel). Suite completa: **238 pruebas, OK**.
- Spring: 3 pruebas nuevas en `ModoMantenimientoIntegrationTest` (escrituras `503` con mensaje y `Retry-After`;
  lecturas e inicio de sesión siguen; se apaga al terminar y sigue en `503` si la base no responde justo después).
  Suite completa: **608 pruebas, 0 fallos**.
- Docker real (QA), con el servicio de respaldo corriendo:

| Caso | Resultado |
| --- | --- |
| Migración | Dos veces sin error; instalación nueva (`script.sql`, `seed.sql` y la migración) sin errores |
| Usuaria normal / confirmación mal escrita | `403` / `400` |
| Restaurar la versión `20261124T023353Z` | `202`; el servicio la tomó en ≤15 s, hizo el respaldo de seguridad `20261124T023509Z`, la aplicó en 33 s: `COMPLETADA`. La publicación hecha después de la versión desapareció |
| Mantenimiento | Mientras duró (23 s), **13 de 13** escrituras a Spring respondieron `503` con el mensaje y `Retry-After: 60`; el panel también `503`; las lecturas `200`. Al terminar, escribir volvió a `204` |
| Deshacer desde el panel (versión de seguridad) | `COMPLETADA`; la publicación volvió |
| Versión listada pero sin archivos | `FALLIDA` en 8 s con el motivo real en el panel; el mantenimiento se apagó solo y escribir volvió a `204` |
| Repetir una restauración correcta con el código final | `COMPLETADA` en 17 s, con su respaldo de seguridad |

- Encontrado al probar: justo después de pedirla, una escritura puede pasar durante los 2 segundos que dura el
  chequeo guardado. No hay riesgo de perder datos por eso: el servicio toma la solicitud segundos después y el
  respaldo de seguridad se hace en ese momento, con esa escritura incluida.
