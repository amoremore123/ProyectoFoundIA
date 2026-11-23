# H69 — Notificaciones masivas

Responsable del backend: Leonel (Django). Web: Yajaira (envío masivo en el panel, con vista previa, límite y
confirmación). Android: Amore (los recibe como avisos normales; no necesita cambios). Backlog 11.5. Prioridad
«Si sobra».

El administrador envía un mismo aviso a muchas cuentas a la vez («La biblioteca cierra el lunes por feriado»).
Como pide el Sprint 4: **con límite, confirmación y registro de quién lo envió y a quién**.

## Cómo funciona

1. **Vista previa:** el administrador escribe el aviso y elige a quién va. El servidor responde cuántas
   cuentas lo recibirán, unas pocas de muestra y un **código de confirmación**.
2. **Confirmación:** para enviarlo hay que mandar el mismo contenido con ese código. El código vale 10 minutos
   y solo sirve para ese contenido y ese grupo: si algo cambió (el texto o la cantidad de cuentas), hay que
   volver a pedir la vista previa. Así nadie envía algo distinto de lo que vio.
3. **Envío:** se crea un aviso en la bandeja de cada cuenta, todos en la misma operación (si algo falla, no se
   envía a nadie). Los avisos son de tipo `SISTEMA`: salen por push o por correo según el canal que cada
   persona eligió para «sistema» (H68), y la app los muestra como cualquier otro aviso.
4. **Registro:** queda el envío con su autor, fecha, contenido y número de destinatarios, y cada aviso queda
   ligado al envío, así se puede ver a quién llegó y quién lo leyó.

## Límites

| Límite | Valor | Si se supera |
| --- | --- | --- |
| Destinatarios por envío | 1000 | `400`: elegir un grupo más chico |
| Envíos masivos por día (Lima), entre todos los administradores | 3 | `429` |
| Título / mensaje | 3 a 150 / 3 a 1000 caracteres | `400` |

Los 1000 destinatarios caben en la ventana de 30 minutos del correo de H68 (50 por minuto).

## Destinatarios

Solo cuentas `ACTIVO` (no las suspendidas ni las eliminadas).

| `destinatarios` | Quiénes |
| --- | --- |
| `TODOS` | Todas las cuentas activas |
| `USUARIOS` | Rol `USUARIO` |
| `BIBLIOTECA` | Rol `BIBLIOTECA` |

## Rutas (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `POST` | `/api/admin/envios-masivos/vista-previa/` | Cuántos lo recibirán, muestra y código de confirmación. No envía nada |
| `POST` | `/api/admin/envios-masivos/` | Enviar, con el código de confirmación |
| `GET` | `/api/admin/envios-masivos/` | Historial, el más reciente primero (paginado) |
| `GET` | `/api/admin/envios-masivos/{id}/` | Detalle |
| `GET` | `/api/admin/envios-masivos/{id}/destinatarios/` | A quién llegó y si lo leyó (paginado) |
| `PUT`, `PATCH`, `DELETE` | `/api/admin/envios-masivos/{id}/` | **No se permiten** (`405`): un envío no se cambia ni se borra |

### Vista previa

```json
POST /api/admin/envios-masivos/vista-previa/
{
  "titulo": "La biblioteca cierra el lunes",
  "mensaje": "Por el feriado, la mesa de objetos atiende el martes desde las 8 a. m.",
  "enlace": "/explorar",
  "destinatarios": "TODOS"
}
```

```json
200
{
  "destinatarios": 128,
  "muestra": [{ "id": 2, "nombre": "Ana Torres", "rol": "USUARIO" }],
  "confirmacion": "3f9c...e1",
  "expiraEn": "2026-11-23T15:40:00Z"
}
```

`enlace` es opcional: una ruta de la web que empieza con `/`, sin dominio ni parámetros. La muestra trae hasta 5
cuentas, sin correos.

### Enviar

El mismo cuerpo más `"confirmacion": "<código>"`. Respuesta `201` con el envío:

```json
{
  "id": 4,
  "titulo": "La biblioteca cierra el lunes",
  "mensaje": "Por el feriado, la mesa de objetos atiende el martes desde las 8 a. m.",
  "enlace": "/explorar",
  "destinatarios": "TODOS",
  "totalDestinatarios": 128,
  "leidas": 0,
  "autor": { "id": 1, "nombre": "Admin FoundIA" },
  "enviadoEn": "2026-11-23T15:32:10Z"
}
```

| Código | Caso |
| --- | --- |
| `400` | Campo inválido; falta `confirmacion`; código vencido, alterado o de otro contenido; ningún destinatario; más de 1000 |
| `401` / `403` | Sin sesión / no es `ADMIN` |
| `429` | Ya se hicieron 3 envíos masivos hoy |

## Base de datos

`database/migracion_h69_envios_masivos.sql` (idempotente, también en `script.sql`):

```sql
CREATE TABLE IF NOT EXISTS envios_masivos (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(150)  NOT NULL,
    mensaje             VARCHAR(1000) NOT NULL,
    enlace              VARCHAR(255)  NULL,
    destinatarios       VARCHAR(12)   NOT NULL,   -- TODOS | USUARIOS | BIBLIOTECA
    total_destinatarios INT           NOT NULL,
    autor_id            BIGINT        NOT NULL,   -- FK usuarios
    enviado_en          DATETIME(6)   NOT NULL
);
ALTER TABLE notificaciones ADD COLUMN envio_masivo_id BIGINT NULL;  -- FK envios_masivos
```

## Lo que necesita la web (Yajaira)

- Formulario con título, mensaje, enlace opcional y grupo; botón «Vista previa» que muestra cuántas cuentas lo
  recibirán y la muestra; botón «Enviar a N cuentas» que pide confirmar y manda el código.
- Si el servidor responde que el código ya no vale, volver a pedir la vista previa.
- Historial con autor, fecha, total y leídas, y el detalle de destinatarios.

## Pruebas que se deben ejecutar y registrar

- Vista previa: cuenta solo activas y por grupo, muestra sin correos, no crea nada.
- Enviar: un aviso por destinatario ligado al envío, de tipo `SISTEMA`, con autor y total registrados.
- Confirmación: sin código, alterado, de otro contenido, de otro grupo, vencido, o si cambió la cantidad de
  cuentas → `400` sin enviar nada.
- Límites: más de 1000 destinatarios `400`; el cuarto envío del día `429`; ningún destinatario `400`.
- Registro: historial, detalle y destinatarios con leídas; `PUT`, `PATCH` y `DELETE` `405`.
- Permisos: un usuario normal `403` en todas las rutas; sin sesión `401`.
- Con MySQL real: la migración dos veces, un envío real llega a la bandeja de cada cuenta por la API de Spring y
  el despachador de correo lo trata según el canal de «sistema».
