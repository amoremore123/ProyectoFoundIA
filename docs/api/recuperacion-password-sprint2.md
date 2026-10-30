# H35 — Recuperar contraseña (Yajaira)

API común para web y Android en Spring Boot `:8081`. Las dos rutas son públicas:
no usan JWT ni lo incluyen en el enlace. Es el contrato que Amore dejó propuesto
para Android (`ApiService.kt`) y se implementó con las mismas rutas y campos.

## 1. Solicitar el enlace

`POST /api/auth/recuperar-password`

```json
{"correo": "ana@example.test"}
```

Respuesta **200**, siempre la misma, exista o no la cuenta:

```json
{"mensaje": "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña."}
```

- `correo` es obligatorio, con formato de correo y como máximo 150 caracteres.
  Si no se cumple, responde 400 con `El campo correo ...`.
- El correo se normaliza (espacios y mayúsculas) antes de buscar la cuenta.
- Solo se envía el correo si la cuenta existe y está ACTIVA. Una cuenta inexistente
  o suspendida recibe la misma respuesta y no se envía nada: **no se puede saber
  qué correos tienen cuenta**.
- Si la misma cuenta pide otro enlace antes de **60 segundos**, se responde igual
  y no se envía otro correo.
- Un enlace nuevo invalida los enlaces anteriores de esa cuenta.
- Si el envío del correo falla, la respuesta es la misma y ese enlace queda
  inutilizable (no se revela el fallo ni la existencia de la cuenta).

El correo contiene `{WEB_URL}/restablecer-password?token=...`. Por defecto
`WEB_URL=http://localhost:5173` (`app.web.url` en `application.yml`). En un celular,
ese enlace abre la web; Android también acepta `foundia://restablecer?token=...`,
pero el backend todavía no genera ese formato.

## 2. Definir la contraseña nueva

`POST /api/auth/restablecer-password`

```json
{"token": "x3Jk...base64url...", "password": "Nueva123!"}
```

Respuesta **200**:

```json
{"mensaje": "Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña."}
```

| Caso | Respuesta |
| --- | --- |
| Token vacío o con formato inválido (base64url, 16–2048 caracteres) | 400 `El campo token ...` |
| Contraseña débil (mínimo 8, mayúscula, minúscula, número y símbolo; mismas reglas que H11) | 400 `El campo password ...` y **el enlace sigue sirviendo** |
| Token inexistente, vencido, ya usado o reemplazado por uno más nuevo | 400 `El enlace no es válido o ya venció. Solicita uno nuevo.` |
| Cuenta suspendida | 400 con el mismo mensaje; la cuenta no se reactiva ni cambia su contraseña |

## Reglas de seguridad

- El token tiene 32 bytes aleatorios (`SecureRandom`). En la base solo se guarda
  su **hash SHA-256**; el token en claro solo viaja en el correo.
- Vence a los **30 minutos** y es de **un solo uso**.
- El cambio es atómico: contraseña, uso del enlace e invalidación de los demás
  enlaces se guardan en la misma transacción.
- Se conservan el estado de la cuenta, la verificación, los intentos fallidos y el
  bloqueo temporal de H12: recuperar la contraseña no quita un bloqueo.
- Se guarda `password_actualizado_en`. En Spring, los JWT emitidos antes de ese
  momento dejan de valer (401): hay que **volver a iniciar sesión**.
- **Limitación conocida:** la API de Django (`:8000`, panel ADMIN) todavía no
  revisa `password_actualizado_en`, así que un token ADMIN anterior al cambio sigue
  sirviendo allí hasta que vence (24 h). Queda pendiente para el Sprint 3, junto con
  H37 (cerrar sesión de forma segura).
- La web quita el token de la barra de direcciones al abrir la página y cierra la
  sesión del navegador después de un cambio correcto.

## Base de datos

Migración `database/migracion_h35_recuperacion_password.sql` (idempotente):

- Tabla `tokens_recuperacion_password` (`token_hash` único, `creado_en`,
  `expira_en`, `usado_en`, FK a `usuarios`).
- Columna `usuarios.password_actualizado_en`, solo si no existe.

Para la base compartida: respaldar antes y aplicar solo esta migración. No
reimportar `script.sql` ni `seed.sql` ni usar `docker compose down -v`.

## Clientes

- Web usuario: `/recuperar-password` (con enlace desde el login, que pasa el correo
  ya escrito) y `/restablecer-password`.
- Android: `RecuperarScreens.kt` usa las mismas rutas. Falta probarlo de punta a
  punta con el APK (ver la entrega de Yajaira).
