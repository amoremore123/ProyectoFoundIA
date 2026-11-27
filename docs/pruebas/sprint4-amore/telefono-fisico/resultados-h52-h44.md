# Pruebas en un teléfono físico: H52 y H44

Fecha: 24/11/2026. Teléfono: moto g(20), Android 11, conectado por USB al QA local (MySQL real, backend de `dev/leonel`).
Cámara real del teléfono; los códigos QR se mostraron en la pantalla de la PC. Las capturas están en esta carpeta.
Hecho por Amore (manejo del teléfono y de la cámara) con comprobación en el servidor.

## H52 · Cámara al publicar

| # | Caso | Resultado esperado | Resultado obtenido |
| --- | --- | --- | --- |
| 1 | Negar el permiso de cámara | Mensaje claro y la galería sigue disponible | **Correcto.** «Sin permiso de cámara. Puedes elegir una fotografía de la galería.»; siguen «Tomar fotografía» y «Elegir fotografía»; la app no se cerró (`tel-h52-5-caso1-negado.png`) |
| 2 | Conceder el permiso y tomar la foto | Vista previa con la imagen de la cámara | **Correcto.** Diálogo del sistema (`tel-h52-2-dialogo-permiso.png`); se vio la vista previa |
| 3 | Publicar con esa foto | La publicación se crea con la foto | **Correcto.** El servidor tiene el objeto 23 con 1 foto JPEG de 115 KiB |

## H44 · Escaneo de etiqueta y QR de retiro (cuenta de biblioteca)

| # | Caso | Resultado esperado | Resultado obtenido |
| --- | --- | --- | --- |
| 1 | Etiqueta y QR de retiro válidos | Entrega registrada | **Correcto.** «Entrega registrada. El objeto quedó como recuperado.» Servidor: reclamo 10 `RETIRADO`, objeto 24 `RECUPERADO` (`tel-h44-3-caso1.png`) |
| 2 | Repetir el mismo QR (ya usado) | Rechazo | **Correcto.** «El reclamo no está aceptado o ya fue retirado.» Sin cambios en el servidor (`tel-h44-7-resultado-caso2.png`) |
| 3 | Etiqueta de un objeto con el QR de otro | Rechazo | **Correcto.** «La etiqueta no corresponde al objeto de este retiro.» Los dos objetos siguen en custodia (`tel-h44-8-caso3.png`) |
| 4 | QR vencido | Rechazo | **Correcto.** «El QR de retiro está vencido.» El objeto sigue en custodia (`tel-h44-9-caso4.png`) |

Además se comprobó la protección contra el campo equivocado: al leer la etiqueta en el campo del QR de retiro, la app dijo
«Ese QR es la etiqueta del objeto. Para este campo escanea el QR de retiro que presenta la persona.» y no registró nada.

## Hallazgos corregidos durante la prueba

- Al **rotar el teléfono se perdía lo escaneado** en «Registrar entrega». Corregido (la actividad ya no se recrea al rotar y los campos
  usan `rememberSaveable`); ver `validacion-emulador-chat.md`.
- El mensaje verde de la entrega anterior se quedaba en pantalla al empezar otra. Corregido.

## Pendiente en teléfono físico

H46 (dictado con ruido y lectura en voz alta), H50 (aviso con la app cerrada) y H51 (TalkBack y texto grande).

## H46 · Dictado y lectura en voz alta (mismo teléfono, 24/11/2026)

| # | Caso | Resultado esperado | Resultado obtenido |
| --- | --- | --- | --- |
| 1 | Dictado en silencio: «perdí una mochila azul en la biblioteca» | El texto aparece y se puede revisar | **Correcto** tras el arreglo (ver abajo): «perdí mi mochila azul en la biblioteca» (`tel-h46-3.png`) |
| 2 | Dictado con ruido (una compañera hablando fuerte al lado) | Anotar si entiende bien, mal o nada | **Correcto**: el campo capturó solo la frase de quien dictaba, sin mezclar lo que decía la compañera (`tel-h46-4-ruido.png`) |
| 3 | Lectura en voz alta de la respuesta de FINDI | Se escucha en español y se detiene | **Correcto**: voz en español; se detiene al salir de la app (tras el arreglo) |

**Hallazgos de la prueba y cómo se corrigieron**
- Al dictar, la app no mostraba que estaba escuchando; tocar otra vez el micrófono (igual de azul) **cancelaba** el dictado y salía «No pude reconocer la voz».
  Registro del teléfono: el reconocedor abrió el micrófono y la propia app lo canceló un segundo después (código 5).
  Ahora hay una franja roja «Preparando el micrófono… / Escuchando… habla ahora», el micrófono se pone rojo, las palabras
  aparecen mientras se habla, tocar el micrófono rojo termina y usa lo dicho, y hay un botón «Cancelar». Cada error del
  reconocedor tiene su mensaje (sin voz, sin conexión, micrófono ocupado, sin permiso).
- La lectura en voz alta **seguía sonando al enviar la app a segundo plano**. Ahora se detiene (y el dictado se cancela) al salir.

**Límite**: la voz (hombre o mujer) la define el motor de texto a voz del teléfono; en este aparato suena de hombre y en otro
sonó de mujer. Se cambia en Ajustes → Texto a voz. La app no la fuerza.
