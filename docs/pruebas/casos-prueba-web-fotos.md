# Casos de prueba — Pantalla de fotos de las publicaciones (H2)

Frontend web: Yajaira (`frontend/web`). API de fotos: Amore (H2,
`POST /api/objetos/{id}/fotos`, JPEG/PNG hasta 5 MiB, formato real verificado en el servidor).
Pruebas automáticas: `frontend/usuario/src/pages/Publicar.test.jsx`.

## Qué se agregó a la pantalla

| Pide el PDF | Antes | Ahora |
| --- | --- | --- |
| Selección y validación | Tipo y tamaño validados | Se mantiene |
| **Mostrar progreso** | Solo "Guardando…" | Barra de progreso accesible (`role="progressbar"`) con "Subiendo foto… N %" |
| La foto corresponde al objeto en el formulario | Solo el nombre del archivo | **Vista previa** de la imagen elegida y botón **Quitar foto** |
| Error y reintento | "Reintentar carga de foto" y "Continuar sin foto" | Se mantiene |
| No anunciar éxito si el servidor rechaza | Correcto | Se mantiene y se prueba con el mensaje real del servidor |
| Conservar la publicación y "Sin foto" | Correcto | Se mantiene |

## Pruebas automáticas

| ID | Caso | Resultado esperado |
| --- | --- | --- |
| CP-WF-01 | Foto válida | Se sube después de crear la publicación, con el avance de la subida |
| CP-WF-02 | Carga fallida y reintento | No crea otra publicación; el reintento sube la foto |
| CP-WF-03 | Formato inválido y tamaño por encima (5 MiB + 1 byte) | Error junto al campo; no se envía |
| CP-WF-04 | Tamaño en el límite (exactamente 5 MiB) | Se acepta |
| CP-WF-05 | Vista previa y Quitar foto | Muestra la imagen elegida; al quitarla desaparece y se libera la memoria |
| CP-WF-06 | Progreso | Barra con `aria-valuenow` y texto "Subiendo foto… 45%" |
| CP-WF-07 | El servidor rechaza la foto | Mensaje del servidor, sin éxito ni salida del formulario; ofrece "Continuar sin foto" |

Suite frontend: **139 pruebas, 0 fallos**.

## Punta a punta en el navegador (Docker) — 20 de octubre de 2026

Cuenta QA temporal; la imagen de prueba se generó en la página (PNG 320×240).
Al terminar se eliminaron la cuenta, las publicaciones, las filas de `fotos` y el
archivo subido (la base volvió a 4 usuarios, 6 objetos y 3 fotos de los datos iniciales).

| Paso | Resultado |
| --- | --- |
| Elegir la foto | Vista previa con la imagen, nombre y "Quitar foto" |
| Publicar | Éxito solo después de guardar la foto; el detalle muestra la misma imagen servida por la API |
| Mis publicaciones | La tarjeta muestra la misma foto |
| Archivo falso (texto con extensión `.png`) | "La publicación quedó guardada, pero no se pudo subir la foto: Formato no admitido: usa JPEG o PNG." · sin éxito · "Reintentar carga de foto" y "Continuar sin foto" |
| Continuar sin foto | El detalle muestra "Sin foto · imagen de referencia" |
| Archivo rechazado en el servidor | No quedó fila en `fotos` ni archivo en `uploads` |
