# Entrega de Amore · Sprint 4 (Android)

Rama: `mobile/android`. Fecha de esta versión: 27/11/2026 (corregida; la del 24/11 se escribió antes de usar el teléfono). Lo que dice «probado en
emulador» se ejecutó en el emulador (Medium_Phone) contra el backend real de QA (MySQL 8). Lo que dice «probado en teléfono»
se ejecutó el 24/11 en un **moto g(20) prestado por una compañera** (Amore solo tiene iPhone). Evidencias en
`docs/pruebas/sprint4-amore/` y `docs/pruebas/sprint4-amore/telefono-fisico/`.

## Qué se entrega

| Historia | Qué hace la app | Estado |
| --- | --- | --- |
| M1 | Teclado sin hueco, «En custodia» en un solo lugar, categorías ordenadas | Probado en emulador |
| M3 | Búsqueda avanzada (ubicación, fechas, radio, chips de filtros) | Probado en emulador contra la API real |
| M4 | Ubicación con GPS, lugares del campus y mapa con el punto de la publicación: **OpenStreetMap** (gratis, sin clave) y Google Maps solo si el equipo configura una clave (la clave va fuera de Git) | GPS y mapa de OpenStreetMap probados en emulador; Google Maps no probado (Google Cloud pide facturación y no se usó) |
| M5 | Editar y eliminar mis publicaciones | Pruebas unitarias |
| M6 | Búsqueda por foto | Pruebas unitarias; la IA real responde 503 en QA sin clave |
| H73 | WebView restringido a nuestro servidor (JavaScript solo del dominio propio, sin acceso a archivos) | Pruebas unitarias y emulador |
| H55 a H63 | Chat: lista, envío, fotos, bloquear, reportar, solo lectura y chat con la biblioteca | Probado en emulador con el backend real |
| H62 | Las fotos del chat son privadas: se piden con la sesión, solo a rutas del chat | Probado en emulador |
| H64 | Foto de perfil propia (ver, cambiar, quitar) y foto de la otra persona dentro del chat | Probado en emulador |
| H65 | Sin conexión: el chat se lee y se escribe, y las publicaciones sin foto quedan en cola; cada envío lleva `Idempotency-Key` | Probado en emulador: se apagó la API, se escribió, y al volver llegó **una sola vez** |
| H68 | Canal (teléfono, correo, ambos o solo bandeja) por cada tipo de aviso | Probado en emulador contra la API real |
| H71 | Avisos destacados arriba de Inicio | Probado en emulador |
| H57 | Registro del teléfono para push, canales `avisos` y `mensajes`, abrir la pantalla del aviso | Registro probado con Firebase real; **push con la app cerrada sin probar** |
| H72 | Entrada a Firebase con el token personalizado | Código y pruebas unitarias; **sin probar de punta a punta** (el QA no tiene la cuenta de servicio) |

## Pruebas

- Unitarias: **159 de 159** (5 corridas limpias seguidas el 27/11). `ExperienciaTest` fallaba de forma intermitente en CI (26/11) por errores de corrutinas de otras pruebas; se pasó a `runBlocking`. No se pudo leer el CI desde aquí (no hay `gh`), así que falta confirmarlo en GitHub.
- Instrumentadas en el emulador: **15 ejecutadas, 0 fallas, 2 omitidas** (necesitan un servidor propio). Incluyen las migraciones
  de Room 1→2→3→4→5 (la 5 es la cola de publicaciones) y la prueba con el proyecto real de Firebase (token de push).
- Chat contra MySQL real: 24 casos de API sin fallas. Backend integrado de Leonel: 13 de 13 casos de API.

## Qué NO está probado y por qué

- **Push con la app cerrada** (semana 12): requiere que el servidor de Leonel envíe con la cuenta de servicio y un teléfono
  o emulador con la app registrada; no se hizo de punta a punta.
- **H72 de punta a punta**: la ruta `/api/auth/firebase-token` responde 503 en el QA sin credenciales; la app sigue con su sesión normal.
- **H51 (TalkBack con voz)**: no se probó en teléfono. Queda sin probar o se declara fuera de alcance por escrito.
- **Probado en teléfono físico (24/11, moto g(20))**: H52 cámara (3 casos), H44 escaneo de etiqueta con cámara real (4 casos) y H46 dictado (3 casos, con correcciones: aviso de «escuchando», mensajes de error y detener la voz al salir). Ver `telefono-fisico/resultados-h52-h44.md`.
- **Voz natural de FINDI (H46)**: la app la pide a `/api/asistente/voz` y, si responde 503 (QA sin `VOZ_API_KEY`/`VOZ_ID`), lee con la voz del teléfono. La voz natural real **no se probó**; solo el respaldo y pruebas unitarias.
- **Rotación**: se corrigió que se perdieran los datos al girar (probado en emulador).
- **Mapa de Google real**: no se usó porque Google Cloud exige una cuenta de facturación. El mapa se muestra con OpenStreetMap (dependencia `osmdroid`, con la atribución que exige su licencia). El código de Google Maps queda y se activa solo si alguien define `foundiaMapsKey`.

## Nota sobre commits

Algunos commits de esta rama mezclan varias historias; no se reescribió el historial. Un archivo `google-services (2).json` se subió por error y se quitó después, pero **sigue en el historial** (es configuración de cliente, no un secreto).

## Límites conocidos

- Solo los mensajes de texto y las publicaciones **sin foto** se encolan sin conexión. Las fotos necesitan conexión.
- Una publicación rechazada por el servidor al reenviarla se descarta sin avisar a la persona (queda para una mejora).
- Los avisos destacados no se guardan en caché a propósito.

## APK

`output/android/FoundIA-Sprint4-Amore-qa-usb.apk` (compilación de depuración, firmada con la llave de depuración). Apunta a
`127.0.0.1` (puertos 18281, 18200 y 18273), así que para usarlo en un teléfono por USB hay que mantener los túneles
`adb reverse` del vigilante de `qa/telefono`. No contiene contraseñas, JWT ni la clave de la cuenta de servicio de Firebase;
lleva la configuración **de cliente** de Firebase (`google-services.json`), que es la que todo APK con Firebase incluye. La clave de
Google Maps está vacía. Un APK de publicación firmado requiere el keystore del equipo.

## Cómo reproducir

1. `docker compose up -d` con el override de QA y aplicar las migraciones del README de `dev/leonel` (con respaldo previo).
2. `adb reverse tcp:18281 tcp:18281` (y 18200, 18273) con el emulador o el teléfono conectado.
3. `./gradlew :app:testDebugUnitTest` y `./gradlew :app:connectedDebugAndroidTest`.
4. Cuentas de QA: las de `database/seed.sql` (ver `docs/integracion/datos-locales-pruebas-sprint2.md`).
