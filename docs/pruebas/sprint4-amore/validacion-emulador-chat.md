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
