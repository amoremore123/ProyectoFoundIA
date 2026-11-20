# H64 — Foto de perfil

Responsable del backend: Leonel (Spring). Web: Yajaira (Perfil y chat). Android: Amore (Perfil y chat).
Prioridad «Debería» del Sprint 4.

## Privacidad

La foto de perfil **no es pública**, a diferencia de las fotos de las publicaciones:

- Solo la ve **la propia persona**, con sesión, en `GET /api/perfil/foto`.
- Cuando exista el chat (H55), la verá también **quien conversa con ella**, con una ruta propia del
  chat que se definirá en ese contrato. Hasta entonces nadie más la ve.
- **No aparece** en las publicaciones, las coincidencias ni ninguna respuesta pública.
- El archivo se guarda en `perfiles/` dentro del volumen de fotos, y la ruta pública
  `/uploads/perfiles/**` está **bloqueada**: responde `401` con o sin sesión y nunca entrega la
  imagen, aunque alguien conozca el nombre. Las fotos de publicaciones siguen públicas.
- Pendiente de decisión del equipo (decisión 5 del reparto, Ley 29733): si también se mostrará en
  las publicaciones. Este contrato aplica la opción más restrictiva.

## Rutas (Spring)

Todas requieren sesión y actúan sobre la cuenta del token.

### `POST /api/perfil/foto`

`multipart/form-data` con la parte `archivo`, con las mismas reglas que las fotos de publicaciones (H2):

- JPEG o PNG verificados por su contenido (no por la extensión), hasta **5 MiB** y 40 megapíxeles.
- Reemplaza la foto anterior y **borra su archivo** del disco después de guardar la nueva.
- Si algo falla al guardar, la foto anterior se conserva y el archivo nuevo se borra.

Respuesta `200`:

```json
{ "fotoPerfilUrl": "/api/perfil/foto?v=3f2a9c1e" }
```

`v` cambia con cada foto nueva para que el navegador y Coil no muestren la anterior desde la caché.

### `GET /api/perfil/foto`

Devuelve la imagen (`image/jpeg` o `image/png`) con `Cache-Control: private, max-age=3600`.
`404` si la cuenta no tiene foto.

### `DELETE /api/perfil/foto`

Quita la foto y borra el archivo. `204`, también si no tenía foto (repetirlo es seguro).

### Perfil y sesión

`GET /api/perfil`, `PUT /api/perfil` y la respuesta de inicio de sesión agregan el campo
`fotoPerfilUrl` (`null` sin foto). Es un campo nuevo: los clientes que no lo usan no cambian.

| Código | Caso |
| --- | --- |
| `200` / `204` | Subida o consulta / borrado |
| `400` | Sin archivo, archivo vacío, más de 5 MiB, no es JPEG ni PNG, más de 40 megapíxeles |
| `401` | Sin sesión |
| `401` | Acceso directo a `/uploads/perfiles/**`, con o sin sesión |
| `404` | `GET` sin foto |

## Base de datos

`database/migracion_h64_foto_perfil.sql` (idempotente, con respaldo previo; también en `script.sql`):

```sql
ALTER TABLE usuarios ADD COLUMN foto_perfil_url VARCHAR(80) NULL;
```

Guarda la ruta interna del archivo (`perfiles/<uuid>.jpg`), nunca una URL pública.

> **Aplicar la migración antes de desplegar este cambio.** Spring usa `ddl-auto: none`: sin la
> columna, las consultas de usuarios fallan.

## Respaldo y recuperación

`perfiles/` está dentro de `uploads_data`, así que entra en el respaldo de fotos (H59) y en la
recuperación (H58) sin cambios.

## Límites

- La eliminación de cuenta (H34) hoy solo registra una solicitud. Cuando se implemente el borrado
  real, debe borrar también la foto de perfil.
- El recorte de la imagen lo hace el cliente (Android y web); el servidor guarda la imagen tal como llega.

## Verificación del 20/11/2026

- Spring: `FotoPerfilIntegrationTest` (**7 pruebas**). Suite completa: **509 pruebas, 0 fallos**.
- MySQL 8.0 de QA: `migracion_h64_foto_perfil.sql` se ejecutó dos veces sin error. Por la API real:
  subir una foto JPEG `200`; `GET /api/perfil/foto` devolvió los mismos bytes con
  `image/jpeg` y `max-age=3600, private`; `fotoPerfilUrl` aparece en el perfil y en el inicio de
  sesión; otra cuenta recibió `404`; un archivo que no es imagen, `400`.
- En la base quedó `perfiles/0c07eabb-….jpg`; `/uploads/perfiles/…` respondió `401` con y sin
  sesión, mientras una foto de publicación siguió respondiendo `200`.
- El respaldo de fotos (H59) incluyó `./perfiles/0c07eabb-….jpg`.

## Pruebas que se deben ejecutar y registrar

- Subir una foto válida: `200`, `GET` la devuelve igual y `fotoPerfilUrl` aparece en el perfil.
- Reemplazar: la nueva se ve, el archivo anterior ya no existe y `v` cambia.
- Borrar: `204`, `GET` `404`, `fotoPerfilUrl` `null`, el archivo ya no existe; repetirlo `204`.
- Archivo que no es imagen, PNG con extensión falsa y archivo de más de 5 MiB: `400`, sin cambios.
- Sin sesión: `401`. Cada cuenta solo obtiene su propia foto.
- `/uploads/perfiles/<archivo>`: nunca `200` (`401` en el servidor real, con y sin sesión).
- La foto no aparece en `GET /api/objetos/{id}` ni en `publicadoPor`.
