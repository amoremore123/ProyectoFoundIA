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

## Lo que pidió Leonel a Android (24/11/2026, emulador + backend integrado de `dev/leonel`)

Se levantó el QA con el código actual de `dev/leonel` y sus 15 migraciones (con respaldo previo de la base de QA) y se
probó contra eso. Script de API: 13 de 13 casos (foto privada con y sin sesión, `Idempotency-Key` `201`/`200`, `otro.tieneFoto`,
canales, aviso destacado creado en el panel y visto sin sesión, registro y baja de dispositivo, `firebase-token` `503` sin credenciales).

| Pedido | Qué se hizo en Android | Cómo se comprobó |
| --- | --- | --- |
| H62: fotos del chat privadas | Se piden con la sesión (`ImagenPrivada`), solo a rutas propias del chat; en memoria, 20 como máximo, se borran al cerrar sesión | Emulador: la foto se ve (`s4-5-chat-foto-privada.png`); una foto mal formada muestra «Foto no disponible» sin romper |
| H64: foto de la otra persona | Avatar en la lista y en el chat cuando `otro.tieneFoto` es `true` | Emulador con Luis viendo la foto de Ana |
| H65: `Idempotency-Key` | La clave se crea antes del primer intento, se guarda en la cola (Room 3→4) y se reutiliza en cada reintento | Pruebas unitarias; migración 3→4 y cola en el emulador (13 de 13 instrumentadas); API: `201` y luego `200` con el mismo mensaje |
| H68: canal por tipo de aviso | Selector por cada uno de los 6 tipos en Preferencias de avisos | Emulador: se cambió «Mensajes del chat» a Correo, se guardó y la API lo devuelve sin tocar los otros cinco (`s4-3-canal-elegido.png`) |
| H71: avisos destacados | Tarjeta arriba de Inicio; no se guarda en caché; si la consulta falla no se muestra nada | Emulador: aviso creado desde el panel y visible sin sesión (`s4-1-inicio-destacado.png`) |
| H57: registro del teléfono | Con el backend real la app obtuvo un token de Firebase y registró el dispositivo (`ANDROID`) | Emulador: `GET /api/dispositivos` lista el dispositivo de Luis |
| H72: token de Firebase | Código y pruebas unitarias hechas | **Sin probar de punta a punta**: el QA no tiene la cuenta de servicio y la ruta responde `503` (la app sigue con su sesión normal) |

Pruebas: 141 unitarias en verde (3 corridas limpias), 13 instrumentadas (2 omitidas por necesitar un servidor propio).
Nota: la primera vez de hoy la prueba del token de Firebase falló porque el emulador acababa de arrancar
(`Firebase Installations Service is unavailable`); la API de Firebase respondía bien desde la PC y, pasados unos minutos,
el emulador obtuvo el token y la prueba pasó.

## H65: publicar sin conexión (24/11/2026, emulador + API real)

La publicación sin foto escrita sin red queda en la cola de Room (versión 5) con su `Idempotency-Key` y la envía un trabajo de
WorkManager en cuanto hay red. Prueba real: se detuvo el contenedor de la API, se publicó «Mochila cola QA» desde la app
(`cola-4-sin-red.png`: «Sin conexión. Tu publicación quedó guardada…»), se reinició la API y la publicación apareció **una sola vez**
(y siguió siendo una sola después de más tiempo). Con foto, la app explica que necesita conexión y no la encola.
Pruebas unitarias: 11 casos de cola (orden, misma clave en cada reintento, rechazo, sesión vencida, cierre de sesión);
instrumentadas: migración 4→5 y persistencia de la cola.

## M4: mapa sin clave de Google (24/11/2026, emulador)

Como Google Cloud exige facturación para la clave de Maps, el detalle de una publicación con coordenadas muestra un mapa de
OpenStreetMap (`mapa-1-osm.png`: mosaicos reales, marcador en el punto y la atribución «© Colaboradores de OpenStreetMap»).
Si algún día se define `foundiaMapsKey`, la app usa Google Maps. En los dos casos queda el botón «Abrir en mapas».
