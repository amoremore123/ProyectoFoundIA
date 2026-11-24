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

## Verificación con MySQL real (22/11/2026, integración en `dev/leonel`)

Proyecto de Compose aislado (`foundia-qa`) con MySQL 8.0, Spring con el chat integrado y los datos de
demostración. **46 casos** de la guía del Sprint 4 por la API real:

| Historia | Comprobado |
| --- | --- |
| H55 | Sin sesión `401`; ningún id o los dos `400`; una tercera persona `403` al abrirla y `404` al leer, escribir o consultar; abrirla dos veces devuelve la misma; mensaje vacío y de 1001 caracteres `400`, de 1000 `201`; cursor `despuesDe`; `sinLeer` y `leida`; el mensaje 21 en un minuto `429`; aviso `MENSAJE_CHAT` sin el texto del mensaje |
| H56 | Bloquear impide escribir a ambos lados (`409`) y desbloquear lo restablece; reportar dos veces no duplica (`204`); reportar un mensaje propio `400` |
| H61 | Tras confirmar la recuperación (H22) la conversación queda en solo lectura: enviar `409` y se sigue leyendo |
| H62 | Foto JPEG `201`; un archivo que no es imagen `400` |
| H63 | Quien reclama abre el chat `CON_BIBLIOTECA`; otra persona `403`; el personal lo ve en su bandeja y responde; quien reclama recibe `MENSAJE_CHAT` |

Correcciones hechas al integrar:

- `migracion_h55_chat.sql` no tenía `USE objetos_perdidos_db;` y fallaba con
  `ERROR 1046 (No database selected)`. Ahora se aplica dos veces sin error, con y sin las tablas creadas.
- **H61:** la conversación entre personas apunta al objeto encontrado, pero al confirmar la recuperación
  cambia el **perdido**, así que nunca quedaba en solo lectura. Ahora también se revisa el objeto perdido
  del contacto. Los estados de reclamo revisados eran `ENTREGADO` y `CANCELADO`, que no existen; ahora son
  `RECHAZADO` y `RETIRADO`. Nueva prueba en `ChatIntegrationTest`, que fallaba sin el arreglo.
- `script.sql`: las 5 tablas del chat se agregaron a los `DROP` del inicio.

Suite de Spring después de integrar: **518 pruebas, 0 fallos**.

### Pendiente de decisión del equipo: privacidad de las fotos del chat (H62)

Las fotos del chat se guardan en la carpeta pública `/uploads/`, como las de las publicaciones. El nombre
es aleatorio e imposible de adivinar, pero **quien tenga la dirección puede abrir la foto sin sesión**
(comprobado: `200` sin token), también después de un bloqueo. Para protegerla habría que servirla con
sesión (como la foto de perfil, H64) y cambiar también la web y Android, que hoy la cargan directamente.

## Cambio del 23/11/2026: fotos del chat privadas (H62)

Hasta ahora las fotos del chat se guardaban en `/uploads/`, la misma carpeta pública de las fotos de las
publicaciones: cualquiera con el enlace podía verlas. Desde este cambio son **privadas**, como la foto de
perfil (H64).

- Se guardan en la subcarpeta `chat/` del volumen de fotos. La ruta pública `/uploads/chat/**` está
  **bloqueada** (`401`), aunque alguien conozca el nombre.
- `imagenUrl` de cada mensaje deja de ser `/uploads/<archivo>` y pasa a ser
  `/api/conversaciones/{id}/mensajes/{mensajeId}/foto`, que pide sesión:

| Quién | ¿Ve la foto? |
| --- | --- |
| Quien participa en la conversación (o el personal, en una conversación con la biblioteca) | Sí |
| `ADMIN`, si ese mensaje fue reportado (para moderarlo en el panel, H56) | Sí |
| Cualquier otra persona, sin sesión o con un mensaje sin foto | No (`404`, o `401` sin sesión) |

- La respuesta lleva `Cache-Control: private, max-age=3600`.
- **Fotos que ya existían:** al arrancar, Spring mueve a `chat/` las fotos de mensajes que todavía están en
  `/uploads/` y actualiza su ruta. Es idempotente; si un archivo ya no existe, igual se deja de apuntar a la
  ruta pública.
- El panel (Django, H56) devuelve el mismo `imagenUrl` de la API en los reportes de mensajes.
- **Web y Android:** pedir la foto con el token de la sesión (como la foto de perfil), no como una imagen
  pública. El cambio no rompe el envío: `POST …/mensajes` con foto sigue igual.

### Verificación del 23/11/2026 (fotos del chat privadas)

- Hasta este cambio H62 no tenía pruebas automáticas (solo la prueba manual en QA). Se agregaron 5 en
  `ChatIntegrationTest`: la foto queda en `chat/` y solo la ven quienes conversan (`200`, caché privada, mismos
  bytes), una tercera persona y otra conversación `404`, sin sesión `401`, la ruta pública del archivo
  bloqueada; el ADMIN la ve solo si el mensaje fue reportado (y el reporte no la abre a nadie más); archivo que
  no es imagen o de más de 5 MiB `400` sin guardar el mensaje; las fotos guardadas con la versión anterior se
  siguen entregando y se pasan a `chat/` una sola vez; con la biblioteca, el personal y quien reclama la ven.
  Django: el reporte devuelve la ruta de la API (1 prueba nueva). Suites completas: **Spring 597, 0 fallos;
  Django 203, OK**.
- MySQL 8.0 de QA, con una foto real del chat guardada con la versión anterior (mensaje 3, entre Ana y Luis):

| Caso | Resultado |
| --- | --- |
| Al arrancar la versión nueva | «Fotos del chat pasadas a la carpeta privada: 1»; el archivo pasó de `/uploads/` a `chat/` y la ruta en la base a `chat/…`. Ninguna publicación ni perfil usaba ese archivo |
| `imagenUrl` que recibe Ana | `/api/conversaciones/1/mensajes/3/foto` |
| Ana / Luis | `200`, `image/jpeg`, `private` / `200` |
| Admin sin reporte / sin sesión | `404` / `401` |
| Archivo por la ruta pública anterior / por `/uploads/chat/` | `404` (ya no está ahí) / `401` |
| Foto de una publicación | Sigue pública: `200` |
| Ana reporta el mensaje → admin con token de Spring / con el del panel (Django) | `200` / `200` |
| Panel (Django), reportes de mensajes | `imagenUrl`: `/api/conversaciones/1/mensajes/3/foto` |
| Reiniciar la API | No vuelve a mover nada (2 arranques, 1 sola vez) |

- **Falta en la web (Yajaira) y en Android (Amore):** pedir la foto del chat con el token de la sesión, como la
  foto de perfil, en lugar de cargarla como imagen pública.
