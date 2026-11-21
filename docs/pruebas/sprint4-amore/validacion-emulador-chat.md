# Validación del chat (H55 a H63) con servicios reales de QA

Fecha: 21/11/2026. Entorno: emulador Android (Medium_Phone), backend de `cierre/sprint4` construido en el
contenedor `foundia-amore-s3-api`, MySQL real de QA y migración `database/migracion_h55_chat.sql` aplicada.
No se usó `down -v` ni se reimportó ningún SQL. **Es una validación en emulador, no en un teléfono físico.**

## API contra MySQL real (26 casos, 0 fallas)

| Caso | Resultado |
| --- | --- |
| Iniciar conversación por contacto y repetirla | 200, la misma conversación |
| Tercera persona abre la conversación / la lee | 403 / 404 |
| Enviar texto; mensaje vacío | 201; 400 |
| Enviar foto PNG | 201 con `imagenUrl` |
| Aviso `MENSAJE_CHAT` a quien recibe | Sí |
| Reportar mensaje ajeno; reportar el propio | 204; 400 |
| Bloquear, escribir bloqueado (ambos lados), desbloquear | 200, 409, 409, 200, 201 |
| Chat con la biblioteca: reclamante escribe, personal ve la bandeja y responde, ajena no accede | 201, 200, 201, 404 |

Nota: el administrador y el personal de la biblioteca no pueden bloquear (regla de `ChatService`); por eso el bloqueo
se probó entre dos usuarios normales (contacto de QA creado solo en la base de pruebas).

## Android en el emulador

- Perfil muestra «Mensajes»; la lista carga las conversaciones reales (`chat-2-lista.png`).
- Abrir una conversación, escribir y enviar: el mensaje aparece y la otra cuenta lo recibe con 2 sin leer (`chat-4-enviado.png`).
- Texto al 130 %: sin recortes en perfil, lista y chat (`h51-130-chat.png`).
- Pruebas unitarias: 116 de 116 (`ChatAndroidTest`, `PushFirebaseTest`, entre otras).

## Pendiente (no se puede cerrar sin lo que se indica)

- Push real con la app cerrada: requiere el proyecto de Firebase y su cuenta de servicio.
- Cámara física, escaneo con la cámara real, dictado con ruido y lectura de TalkBack con voz: requieren un teléfono Android.
- La foto de la otra persona en el chat: espera la ruta que defina el contrato de H64/H55.

## H44: escaneo de etiqueta y QR de retiro

Amore informó el 21/11/2026 que el escaneo y el QR se probaron en su salón y funcionan. **Es un resultado reportado
por la persona responsable, sin capturas ni registro adjunto**, y no se anotó en qué dispositivo se hizo. Antes de
presentarlo como certificado conviene agregar la evidencia (capturas o un video corto) y los casos probados: QR válido,
ya usado, de otro objeto y vencido.

## H64: foto de perfil (21/11/2026, emulador + API real)

Backend de `dev/leonel` integrado en una rama local de QA con el chat. Resultados:
subir por la API `200` y la app muestra la foto (`h64-1-perfil-con-foto.png`); «Quitar foto» en la app borra en el
servidor (`GET /api/perfil/foto` pasa a `404`, `h64-2-sin-foto.png`); el acceso directo a `/uploads/perfiles/**` responde `401`.
La foto de la otra persona dentro del chat espera la ruta que defina el contrato de H55.
Elegir una foto desde la galería del emulador no se automatizó: se probó la subida con la API y la validación con pruebas unitarias.

## H65 y caché del chat: lectura y envío sin conexión (21/11/2026, emulador + API real)

- Room pasa a la versión 3 con `conversaciones_cache`, `mensajes_chat_cache` y `mensajes_pendientes` (migración 2→3 probada en el emulador; 9 de 9 pruebas instrumentadas).
- Sin conexión la lista y el chat muestran lo último guardado; un mensaje de texto escrito sin red queda «Pendiente de enviar» (`off-2-pendiente.png`) y se envía en orden al volver la conexión.
- Prueba real: se detuvo el contenedor de la API, se envió un mensaje desde la app, se reinició la API y el mensaje llegó **una sola vez** a la otra cuenta (`off-3-enviado.png`).
- El chat guardado es privado: se borra al cerrar sesión y al entrar con otra cuenta (probado con pruebas unitarias).
- Pruebas unitarias: 128 de 128.

**Límites que se declaran:** solo los mensajes de texto se encolan (las fotos necesitan conexión); publicar sin conexión no está
implementado (la parte de H65 en el backend, el identificador de cliente para no duplicar, tampoco existe todavía).
Si la respuesta del servidor se pierde después de que el mensaje ya se guardó, un reintento podría duplicarlo hasta que el
backend acepte un identificador de cliente.
