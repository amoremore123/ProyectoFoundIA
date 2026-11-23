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
