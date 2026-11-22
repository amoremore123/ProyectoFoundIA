# Sprint 4 · H55 a H63 — Chat interno

Todas las rutas exigen sesión (JWT). Una persona solo ve las conversaciones donde participa; el personal de la biblioteca (rol BIBLIOTECA o ADMIN) ve además la bandeja de las conversaciones `CON_BIBLIOTECA`. Una conversación ajena responde 404, igual que si no existiera.

## Iniciar o recuperar una conversación
`POST /api/conversaciones`

```json
{ "contactoId": 12 }      // entre quien perdió y quien encontró
{ "reclamoId": 7 }        // entre quien reclama un objeto en custodia y la biblioteca
```

Se indica uno solo. Repetir la llamada devuelve la misma conversación. Errores: 400 (ninguno o ambos), 403 (no eres parte), 404 (no existe), 409 (contacto rechazado o cerrado).

## Listar y consultar
- `GET /api/conversaciones` → lista con `ultimoMensaje` y `sinLeer`.
- `GET /api/conversaciones/{id}` → una conversación.
- `GET /api/conversaciones/{id}/mensajes?despuesDe=0` → hasta 50 mensajes con id mayor al cursor, más `ultimoId`, `soloLectura` y `bloqueada`. La app consulta cada pocos segundos con el último id recibido.

## Enviar
- `POST /api/conversaciones/{id}/mensajes` con `{ "texto": "..." }` (1 a 1000 caracteres).
- Con foto (H62): `multipart/form-data` con la parte `foto` (JPEG o PNG, hasta 5 MiB) y `texto` opcional.
- 409 si la conversación está en solo lectura (H61) o hay un bloqueo. 429 si se superan 20 mensajes por minuto.
- El otro participante recibe una notificación `MENSAJE_CHAT` sin el texto del mensaje.

## Leído, bloqueo y reporte
- `POST /api/conversaciones/{id}/leida` → 204; deja `sinLeer` en 0.
- `POST /api/conversaciones/{id}/bloqueo` y `DELETE …/bloqueo` (H56): bloquear impide escribir a ambos lados.
- `POST /api/conversaciones/{id}/mensajes/{mensajeId}/reporte` con `{ "motivo": "..." }` → 204. Repetirlo no duplica; no se puede reportar un mensaje propio (400).

## Solo lectura (H61)
Pasa a solo lectura cuando el objeto queda `RECUPERADO`, `OCULTO` o `ELIMINADO`, o cuando el reclamo o el contacto se cierran. Se sigue pudiendo leer.

## Base de datos
Tablas `conversaciones`, `mensajes_chat`, `lecturas_chat`, `bloqueos_chat`, `reportes_mensaje`. Migración idempotente: `database/migracion_h55_chat.sql`.
