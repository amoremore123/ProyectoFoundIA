# Planificación del Sprint 4: cierre del proyecto

Borrador del 17/11/2026 para el Planning Poker. El Sprint 4 es el último: reúne
las historias del backlog que faltan, lo que quedó atrás en móvil y lo que exige
el curso de Programación Móvil (semanas 7 a 14). Las horas son una estimación
de backend, web y Android que hay que confirmar con el equipo. Nada de esto está
implementado todavía.

Reparto por capas: **Leonel hace el backend, Yajaira el front y Amore Android.**
Cada uno tiene su lista de historias y tareas en orden en la sección 6.

Fuentes: el backlog (Word del equipo, 80 historias en 15 épicas), los repartos
de los Sprints 1, 2 y 3 y el documento de pendientes del Sprint 2 (PDF), el
temario del curso, la entrega de Yajaira del Sprint 3 (13/11) y la
[entrega de Amore del Sprint 3](../integracion/entrega-amore-sprint3.md). Los PDF
y el Word no están en el repositorio.

## 1. Resumen

- **Estado del backlog.** De las 80 historias, 58 están hechas, 10 quedaron a
  medias (seis por la capa móvil) y 12 están sin empezar. Las 12 pendientes
  suman 72 puntos y las 10 a medias, 47.
- **Lo que más pesa.** El chat interno (Épica 9) no existe: no hay tablas ni
  rutas. También faltan las campañas de difusión (Épica 15), las notificaciones
  masivas, el modo sin conexión y parte de los respaldos.
- **El temario del curso agrega trabajo a Android.** La app no tiene ubicación,
  Firebase, Google Cloud ni WebView, y las semanas 11 a 14 lo exigen.
- **Cuello de botella.** Android. Lo que debe entrar ocupa 63 h y Amore tiene 57.
  Hay que decidir qué se hace (sección 9).
- **Cierre.** Además de las historias, el sprint cierra el proyecto: integración
  en `main` con autorización, despliegue con HTTPS, pruebas de punta a punta,
  documentación final, video de respaldo y ensayo con el jurado.

## 2. Cómo llegamos al Sprint 4

| Sprint | Alcance | Resultado |
| --- | --- | --- |
| 1 | H1 a H20. El 2/10 eran siete historias (H11, H12, H1, H3, H4, H6 y H7). El 13/10 se sumaron las trece restantes | API y web terminadas. En móvil quedó una base pendiente: H8 a H10, la edición y eliminación de publicaciones, la cámara y el reclamo. La cámara y el reclamo se hicieron en el Sprint 3; las otras siguen pendientes |
| 2 | H21 a H40. Se tomaron 14 historias | Entregadas las 14. Pasaron al Sprint 3 H25, H32, H36, H37, H38 y H40 |
| 3 | 20 historias: las 6 arrastradas y las nuevas H41 a H54 | Terminadas según las entregas de Yajaira y de Amore. Faltan las pruebas físicas de Amore y llevar el sprint a `main` |
| 4 | Las historias restantes, el temario del curso y el cierre del proyecto | Este documento |

Las historias H41 a H54 (custodia, IA, accesibilidad y otras) no están en el
backlog en Word, así que no cuentan en el balance de la sección 3.

No hay en el repositorio la entrega de Leonel del Sprint 3. Su estado se tomó de
lo que declara Yajaira y del código. Hay que confirmarlo con él.

## 3. Estado del backlog

Las épicas 5 a 8 y 10 no tienen título en el Word. Los nombres de la tabla
salen de su contenido.

| Épica | Hechas | A medias | Sin empezar | Qué falta |
| --- | --- | --- | --- | --- |
| 1 Publicación de objetos | 3 | 2 | 0 | Móvil: ubicación con GPS (1.3) y editar o eliminar (1.5) |
| 2 Búsqueda y filtros | 2 | 3 | 0 | Móvil: ubicación, fechas y combinar filtros (2.3 a 2.5) |
| 3 Conexión perdido-encontrado | 5 | 0 | 0 | |
| 4 Gestión de usuarios | 5 | 0 | 0 | |
| 5 Avisos por eventos | 3 | 1 | 1 | 5.3 aviso de mensaje de chat. 5.5 elegir el canal, push o correo |
| 6 Categorías y moderación | 5 | 0 | 0 | |
| 7 Historial y métricas | 5 | 0 | 0 | |
| 8 Web, app y accesibilidad | 4 | 1 | 0 | 8.2: la app debe mostrar lo mismo que la web |
| 9 Chat interno | 0 | 0 | 5 | Toda la épica |
| 10 Acceso, privacidad y cuenta | 5 | 0 | 0 | |
| 11 Notificaciones | 5 | 0 | 1 | 11.5 notificaciones masivas |
| 12 Seguridad y privacidad | 6 | 0 | 0 | |
| 13 Multiplataforma | 5 | 0 | 1 | 13.3 sincronización y modo sin conexión |
| 14 Respaldo de datos | 3 | 3 | 0 | 14.2 recuperar tras una falla, 14.4 publicaciones sin perderse y 14.6 restaurar una versión anterior |
| 15 Publicidad y difusión | 2 | 0 | 4 | 15.1 campañas, 15.2 promociones en redes, 15.3 alcance y 15.5 avisos destacados |
| **Total** | **58** | **10** | **12** | |

Dos hallazgos del código que cambian el plan:

- **Las fotos no se respaldan.** El respaldo de H38 es un volcado de la base
  de datos. Los archivos subidos, que viven en el volumen `uploads_data`, no se
  incluyen. Por eso 14.4 («que mis publicaciones no se pierdan») queda a medias.
- **El panel sí permite restaurar publicaciones.** Pasar de OCULTO a ACTIVO
  funciona, así que la 6.2 está hecha.

## 4. Temario de Programación Móvil: lo que Android debe cumplir

Se revisó el código de la app contra cada semana.

| Semana | Tema | Hoy | Falta | Tarea |
| --- | --- | --- | --- | --- |
| 7 | Patrón MVVM | Cumple: ViewModels y repositorios, con 53 pruebas unitarias | Las pantallas nuevas siguen el patrón | |
| 8 | Room | Cumple: 3 tablas, migración 1 a 2 y 6 pruebas instrumentadas | Caché de conversaciones del chat | H55 |
| 9 y 10 | Retrofit y API REST | Cumple: 43 rutas contra Spring y Django | Rutas de chat, dispositivos y Firebase | H55, H57 y H72 |
| 11 | Localización y permisos de hardware | Parcial: permisos de cámara, micrófono y notificaciones. **No hay ubicación** | Permiso de ubicación, GPS y lugares del campus | M4 |
| 12 | Notificaciones simples y push | Parcial: notificaciones simples con WorkManager (H50). **No hay push** | Push con Firebase Cloud Messaging | H57 |
| 13 | Google Cloud y Firebase | **No hay nada** | Firebase (push, Auth, Analytics y Crashlytics) y Google Maps | H57, H72 y M4 |
| 14 | Desarrollo híbrido con WebView | **No hay WebView** | Páginas web de FoundIA dentro de la app | H73 |

Cómo se cubre cada una:

- **Semana 11.** M4 pide el permiso de ubicación, obtiene el lugar y las
  coordenadas y las guarda con la publicación.
- **Semana 12.** H57 reemplaza la consulta periódica por push real. En la prueba
  de H50 Android retrasó el trabajo de WorkManager, que no sirve para un chat.
- **Semana 13.** Firebase Cloud Messaging y Analytics y Crashlytics (H57), Firebase
  Auth con token personalizado (H72), y Google Maps como servicio de Google Cloud
  (M4). El detalle de Auth está en `movil/android/docs/PREPARACION_SPRINT_3.md`.
- **Semana 14.** H73 abre dentro de la app los Términos, la Privacidad y la
  verificación pública de la etiqueta, con la lista de sitios permitidos
  limitada a nuestro propio servidor.

Las claves de Google (Maps y Firebase) no se suben a Git.

## 5. Historias del Sprint 4

Los números H55 en adelante son provisionales. Las horas son backend, web y
Android en ese orden. Los puntos son los del backlog. Donde la historia es
nueva, es una propuesta. Con la división por capas, **todo el backend es de
Leonel y toda la web es de Yajaira.**

### Debe entrar

| ID | Historia | Backlog | Pts | Back | Web | Android |
| --- | --- | --- | --- | --- | --- | --- |
| H55 | Conversar por chat interno y ver mis conversaciones | 9.1 y 9.2 | 18 | 10 | 8 | 10 |
| H56 | Bloquear o reportar desde el chat | 9.4 | 3 | 3 | 2 | 3 |
| H57 | Firebase y aviso de mensaje con push | 5.3 y semanas 12 y 13 | 5 | 3 | 1 | 8 |
| H58 | Recuperar los datos después de una falla | 14.2 | 8 | 6 | 1 | 0 |
| H59 | Respaldar también las fotos | 14.4 | 5 | 4 | 0 | 0 |
| H60 | Campañas de difusión | 15.1 | 5 | 3 | 3 | 0 |
| H72 | Ingresar con Firebase Auth | Semana 13 | | 6 | 0 | 7 |
| H73 | Páginas web dentro de la app con WebView | Semana 14 | | 0 | 2 | 5 |
| M1 | Correcciones de móvil: teclado, estado en custodia y categorías | Varias | | 1 | 0 | 6 |
| M2 | Pruebas físicas del Sprint 3: H44, H46, H50, H51 y H52 | Sprint 3 | | 0 | 0 | 8 |
| M4 | Ubicación con GPS, lugares del campus y mapa de Google | 1.3 y semanas 11 y 13 | 3 | 0 | 0 | 11 |
| | **Subtotal** | | | **36** | **17** | **58** |

### Debería entrar

| ID | Historia | Backlog | Pts | Back | Web | Android |
| --- | --- | --- | --- | --- | --- | --- |
| H61 | Chat en solo lectura al recuperar el objeto | 9.5 | 3 | 1 | 1 | 1.5 |
| H62 | Fotos en el chat | 9.3 | 5 | 3 | 2 | 4 |
| H63 | Chat con la biblioteca por reclamo | Nueva | 8 | 4 | 4 | 5 |
| H64 | Foto de perfil | Nueva | 5 | 4 | 3 | 5 |
| H65 | Sincronización y modo sin conexión | 13.3 | 8 | 1 | 0 | 10 |
| H66 | Promociones en redes sociales | 15.2 | 5 | 2 | 3 | 0 |
| H67 | Medir el alcance de las campañas | 15.3 | 8 | 5 | 4 | 0 |
| M3 | Búsqueda avanzada en móvil | 2.3 a 2.5 | 13 | 0 | 0 | 6 |
| M5 | Editar y eliminar mis publicaciones en móvil | 1.5 | 5 | 0 | 0 | 5 |
| | **Subtotal** | | | **20** | **17** | **36.5** |

### Si sobra tiempo

| ID | Historia | Backlog | Pts | Back | Web | Android |
| --- | --- | --- | --- | --- | --- | --- |
| H68 | Elegir el canal de avisos | 5.5 | 3 | 3 | 1 | 1 |
| H69 | Notificaciones masivas | 11.5 | 7 | 4 | 3 | 1 |
| H70 | Restaurar una versión anterior | 14.6 | 5 | 3 | 2 | 0 |
| H71 | Avisos destacados | 15.5 | 5 | 2 | 3 | 2 |
| M6 | Búsqueda por foto en móvil | Paridad | | 0 | 0 | 4 |
| | **Subtotal** | | | **12** | **9** | **8** |

La 13.3 (H65) es de prioridad Alta en el backlog y aquí está en «Debería» por la
capacidad de Android. M3 y M5 son paridad con la web y también quedan en
«Debería» porque lo que exige el curso va primero.

### Cierre del proyecto

| ID | Tarea | Quién | Horas |
| --- | --- | --- | --- |
| C1 | Integración final, despliegue con HTTPS y etiqueta de entrega en `main` (con autorización del equipo) | Leonel | 13 |
| C2 | Pruebas de punta a punta de la web con Playwright y regresión | Yajaira | 6 |
| C3 | Pruebas de punta a punta de Android y APK de entrega | Amore | 3 |
| C4 | Documentación final, video de respaldo y ensayo con el jurado | Leonel 4, Yajaira 5 y Amore 2 | 11 |

### Carga por integrante

La capacidad es la real del Sprint 3: Leonel 58 h, Yajaira 60.5 h y Amore 57 h.
Hay que confirmar la duración del Sprint 4.

| Integrante | Debe entrar (con cierre) | Debería | Si sobra | Capacidad |
| --- | --- | --- | --- | --- |
| Leonel (backend) | 53 h | +20 h | +12 h | 58 h |
| Yajaira (front) | 28 h | +17 h | +9 h | 60.5 h |
| Amore (Android) | 63 h | +36.5 h | +8 h | 57 h |

- **Android no cabe.** Lo que debe entrar son 63 h y Amore tiene 57: faltan 6 h.
- **Leonel llena su capacidad.** Después de lo que debe entrar le quedan 5 h, y
  eso alcanza solo para H61 y H62. Quedan 16 h de backend de «Debería» sin dueño
  (H63 a H67) y 12 h de «Si sobra».
- **Yajaira tiene unas 32 h libres**, pero no puede hacer su web de esas
  historias sin el backend. La sección 9 propone cómo resolverlo.

## 6. Reparto por integrante, en orden

Cada lista sigue el orden de ejecución. La línea de capacidad marca hasta dónde
llega cada persona. Las horas acumuladas incluyen el cierre del proyecto.

### 6.1 Leonel: backend

Capacidad: 58 h. Cada historia empieza con su contrato en `docs/api/sprint4-hxx.md`
(rutas, JSON y errores 400, 401, 403, 404 y 409).

| Orden | Historia y tareas | Horas | Acumulado |
| --- | --- | --- | --- |
| 1 | **M1, datos de categorías.** Desactivar «Accesorios QA semanal» y «Accesorios QA React» desde el panel y comprobarlo en la base de QA y en la de cada compañero | 1 | 1 |
| 2 | **H59, respaldar también las fotos.** (a) Definir qué se respalda del volumen `uploads_data` y su retención. (b) Ampliar el servicio de respaldo con un archivo comprimido y su huella SHA-256, sin tocar volúmenes en uso. (c) Registrar el resultado junto al respaldo de la base. (d) Probar la restauración de fotos en una carpeta aislada | 4 | 5 |
| 3 | **H58, recuperar los datos después de una falla.** (a) Procedimiento escrito de recuperación de base y fotos. (b) Script de recuperación completa en un entorno aislado. (c) Simular la pérdida de los volúmenes en QA y recuperar. (d) Comprobar que publicaciones, fotos y cuentas coinciden con antes. (e) Medir el tiempo y registrar la evidencia. (f) Declarar los límites | 6 | 11 |
| 4 | **H72, Firebase Auth, backend.** (a) Contrato y cuenta de servicio de Firebase, con la clave solo en variables de entorno. (b) `POST /api/auth/firebase-token`: token personalizado con un UID estable ligado al id interno. (c) Revocar el token de Firebase al cerrar sesión (H37). (d) Si una API acepta tokens de Firebase, verificarlos y resolver la cuenta interna. (e) Pruebas: sesión ajena, token revocado y cuenta suspendida | 6 | 17 |
| 5 | **H57, push, backend.** (a) Contrato. (b) Tabla de dispositivos con `POST` y `DELETE /api/dispositivos`. (c) Enviar el push al crear un aviso o un mensaje, respetando las preferencias. Pruebas con cuenta ajena | 3 | 20 |
| 6 | **H55, chat, backend.** (a) Contrato. (b) Migración idempotente con respaldo: `conversaciones`, `participantes` y `mensajes`. (c) Crear una conversación solo con contacto o reclamo aceptado. (d) Enviar y listar mensajes con el cursor `despuesDe`. (e) Marcar como leído y contar los no leídos. (f) Límites de tamaño y de frecuencia, sin datos de contacto. (g) Pruebas: participante ajeno, objeto recuperado y envío repetido | 10 | 30 |
| 7 | **H56, bloquear o reportar.** (a) Tabla `bloqueos_chat` y `POST /api/usuarios/{id}/bloqueo`. (b) Reportar un mensaje reutilizando los reportes de H27. (c) Pruebas | 3 | 33 |
| 8 | **H60, campañas, backend.** (a) Modelo de campaña con nombre, fechas y enlace con su origen. (b) Rutas de administración en Django. (c) Pruebas de permisos | 3 | 36 |
| 9 | **Cierre C1.** (a) Integrar `dev/yajaira`, `dev/amore` y `mobile/android` en `dev/leonel` sin forzar ramas (4 h). (b) Despliegue con HTTPS (6 h). (c) Etiqueta de entrega en `main`, con autorización del equipo (3 h) | 13 | 49 |
| 10 | **Cierre C4.** Documentación del backend, video de respaldo y ensayo | 4 | 53 |
| | **Línea de capacidad: quedan 5 h** | | |
| 11 | **H61 (1 h) y H62 (3 h)**, backend del chat en solo lectura y de las fotos en el chat | 4 | 57 |

Lo que no cabe en el backend de Leonel: H63 (4 h), H64 (4 h), H65 (1 h), H66
(2 h) y H67 (5 h), que suman 16 h, más 12 h de «Si sobra».

### 6.2 Yajaira: front (web de usuario y panel)

Capacidad: 60.5 h. Cada historia espera al contrato y a la API de Leonel.

| Orden | Historia y tareas | Horas | Acumulado |
| --- | --- | --- | --- |
| 1 | **H73, páginas para WebView.** Versión embebida (`?embebido=1`) de `/terminos`, `/privacidad` y `/custodia/verificar`, sin menú ni pie. Probarlas dentro de la app | 2 | 2 |
| 2 | **H55, chat, web.** (a) Servicio en `api.js`. (b) Lista de conversaciones. (c) Pantalla de chat con envío. (d) Mensajes sin leer. (e) Estados de carga, error y vacío. (f) Prueba con la API real | 8 | 10 |
| 3 | **H56, bloquear o reportar en el chat** | 2 | 12 |
| 4 | **H57, aviso de mensaje nuevo** en Notificaciones, con su preferencia | 1 | 13 |
| 5 | **H58, respaldos en el panel.** Mostrar el último respaldo de la base y de las fotos y el procedimiento de recuperación | 1 | 14 |
| 6 | **H60, campañas, web.** Panel con la lista, el alta y la edición de campañas | 3 | 17 |
| 7 | **Cierre C2.** Pruebas de punta a punta con Playwright y regresión | 6 | 23 |
| 8 | **Cierre C4.** Documentación web, video de respaldo y ensayo | 5 | 28 |
| | **Debe entrar completo: 28 h. Siguen, en este orden, los de «Debería»** | | |
| 9 | H61 (1 h) y H62 (2 h), web del chat en solo lectura y de las fotos | 3 | 31 |
| 10 | H63, chat con la biblioteca, en la bandeja del panel | 4 | 35 |
| 11 | H64, foto de perfil, web | 3 | 38 |
| 12 | H66, promociones en redes: preparar el texto y el enlace y registrar la publicación | 3 | 41 |
| 13 | H67, alcance de las campañas: tablero por origen | 4 | 45 |
| | **Si sobra:** H68 (1 h), H69 (3 h), H70 (2 h) y H71 (3 h) | 9 | 54 |

Del 9 en adelante Yajaira necesita el backend de Leonel, que no cabe en su
capacidad. Ver la sección 9.

### 6.3 Amore: Android

Capacidad: 57 h.

| Orden | Historia y tareas | Horas | Acumulado |
| --- | --- | --- | --- |
| 1 | **M2, pruebas físicas del Sprint 3** con el teléfono: H52 cámara real y permiso (1.5 h), H44 escaneo de etiqueta y QR válido, usado, de otro objeto y vencido (2.5 h), H46 dictado con ruido (1.5 h), H50 aviso con la app cerrada (1 h) y H51 TalkBack y texto al 130 % (1.5 h). Capturas en `docs/pruebas/` | 8 | 8 |
| 2 | **M1, correcciones.** (a) Teclado en inicio de sesión y registro (2 h). (b) Estado en custodia: «En custodia» en un solo lugar y no pedir coincidencias a un objeto en custodia (3 h). (c) Ordenar las categorías (1 h) | 6 | 14 |
| 3 | **M4, ubicación (semanas 11 y 13).** (a) Permiso de ubicación, con los casos negado y negado para siempre (1.5 h). (b) «Usar mi ubicación» con el lugar (2.5 h). (c) Lugares del campus como en la web (1.5 h). (d) Guardar coordenadas y mostrarlas en el detalle (1.5 h). (e) Mapa de Google con marcador en el detalle, con la clave fuera de Git (2.5 h). (f) Pruebas sin permiso, sin GPS y sin red (1.5 h) | 11 | 25 |
| 4 | **H57, Firebase y push (semanas 12 y 13).** (a) Proyecto Firebase, `google-services.json` fuera de Git y dependencias (1.5 h). (b) Registrar y renovar el token en la API (1.5 h). (c) Servicio de mensajes y canal «Mensajes» (1.5 h). (d) Abrir la pantalla correcta al tocar el aviso (1 h). (e) Analytics y Crashlytics (1 h). (f) Pruebas con la app cerrada, permiso negado y cuenta ajena (1.5 h) | 8 | 33 |
| 5 | **H72, Firebase Auth (semana 13).** (a) Dependencia y configuración (1 h). (b) Tras iniciar sesión en FoundIA, pedir el token personalizado y entrar con `signInWithCustomToken` (2 h). (c) Cerrar sesión de Firebase y borrar los tokens (1 h). (d) Estado de sesión coherente al abrir la app y al vencer (1.5 h). (e) Pruebas con token vencido, otra cuenta y sin red (1.5 h) | 7 | 40 |
| 6 | **H73, WebView (semana 14).** (a) Pantalla con lista de sitios permitidos: solo nuestro servidor (1.5 h). (b) Progreso, error con reintento y botón atrás (1 h). (c) Abrir Términos, Privacidad y verificación de etiqueta dentro de la app (1 h). (d) Seguridad: JavaScript solo del dominio propio, sin acceso a archivos, sin puente y sin enviar el JWT (0.75 h). (e) TalkBack y texto ampliado (0.75 h) | 5 | 45 |
| 7 | **Cierre.** C3, pruebas de punta a punta de Android y APK de entrega (3 h), y C4, documentación móvil y video (2 h) | 5 | 50 |
| | **Línea de capacidad: quedan 7 h** | | |
| 8 | **H55, chat, Android (semana 8).** (a) Servicio Retrofit y modelos (1 h). (b) Room: caché de conversaciones y mensajes (2 h). (c) Lista de conversaciones (1.5 h). (d) Pantalla de chat con envío y consulta periódica (2.5 h). (e) Estados vacío, error y sin red (1.5 h). (f) Pruebas (1.5 h) | 10 | 60 |
| 9 | **H56, bloquear o reportar desde el chat** | 3 | 63 |
| | **Debe entrar completo: 63 h.** Siguen los de «Debería»: M3 (6 h), M5 (5 h), H61, H62, H63, H64 y H65 | 36.5 | 99.5 |

El chat de Android (filas 8 y 9) es lo que no cabe: faltan 6 h.

## 7. La parte de Amore: capturas, paridad, chat y foto de perfil

### 7.1 Lo que se vio en las capturas

| Captura | Qué pasa | Causa encontrada | Tarea |
| --- | --- | --- | --- |
| Inicio de sesión con el teclado | Queda un hueco azul entre el formulario y el teclado, y el campo de la contraseña se corta | Hipótesis a comprobar: el manifiesto usa `adjustResize` y las pantallas añaden `imePadding()`, así que el teclado se compensa dos veces | M1 |
| Detalle de un objeto en custodia | Se ve el texto técnico `EN_CUSTODIA` junto a la etiqueta y aparece «No se pudieron actualizar las coincidencias» | Comprobado: `GET /api/objetos/{id}/coincidencias` responde 404 a todos los usuarios para un objeto en custodia, porque no es público, y la app lo muestra como error. La web muestra «En custodia» | M1 |
| Categorías al publicar | Hay dos que no tienen sentido | «Accesorios QA semanal» (id 9) y «Accesorios QA React» (id 10) son datos de pruebas del panel. No están en `database/seed.sql` ni en el código | M1 |
| Registrar entrega con error genérico | «No se pudo completar la operación» | Corregido el 17/11 en el commit `e5ec7e3`: validación del QR escaneado y mensajes reales de Django. Falta repetir la prueba con el teléfono | M2 |
| «No hay conexión con FoundIA» | La app no llega al servidor | No es un fallo de la app. Faltaban los túneles USB; ya los mantiene el vigilante de `qa/telefono` | Ninguna |

Se asume que las dos categorías que no tienen sentido son las dos «Accesorios
QA». Hay que confirmarlo.

### 7.2 Paridad con la web

Se comparó lo que llama la web de usuario con lo que llama la app. La API ya
ofrece casi todo.

| Función | Web | Móvil | Tarea |
| --- | --- | --- | --- |
| Registro, ingreso, perfil y recuperar contraseña | Sí | Sí | |
| Publicar con foto o cámara, y sugerencia de FINDI | Sí | Sí | |
| Coincidencias, reclamo en custodia, QR de retiro y escáner | Sí | Sí | |
| Avisos, historial, preferencias, reportar, compartir e invitar | Sí | Sí | |
| Buscar por texto y categoría | Sí | Sí | |
| **Búsqueda avanzada**: ubicación, fechas, radio por cercanía y chips de filtros activos | Sí | No. La API ya admite `desde`, `hasta`, `latitud`, `longitud` y `radioKm`, pero la app solo envía `q`, `tipo`, `categoriaId` y `ubicacion` | M3 |
| **Ubicación al publicar**: «Usar mi ubicación» con GPS y coordenadas guardadas | Sí | No. Es un campo de texto libre | M4 |
| **Editar y eliminar mis publicaciones** | Sí | No hay pantalla. La API y el repositorio ya existen y no los usa ninguna pantalla | M5 |
| **Búsqueda por foto** | Sí | No | M6 |

- **M3 (6 h).** Ampliar el servicio Retrofit y el repositorio, y añadir
  ubicación, fechas, radio y chips de filtros activos. No cambia la API.
- **M5 (5 h).** Editar y eliminar desde Mis reportes, con confirmación. No se
  edita lo que está en custodia o recuperado, igual que la web.

### 7.3 Mensajería: ¿tiene sentido?

Sí, y la Épica 9 del backlog ya la pide. Conviene distinguir dos conversaciones.

1. **Entre personas.** Para un objeto que sigue con quien lo encontró, una vez
   aceptado el contacto o el reclamo (9.1): coordinar la entrega sin compartir el
   número.
2. **Con la biblioteca.** Una conversación por reclamo en custodia (H63). La
   biblioteca pide un detalle más para verificar, acuerda cuándo retirar y avisa
   que el objeto llegó. No está en el backlog.

No tiene sentido un chat libre entre quien encontró y quien reclama cuando el
objeto ya está en la biblioteca: quien lo encontró ya no lo tiene. Por eso el
flujo de custodia reemplazó a «Contactar» (H42).

Reglas de privacidad:

- Nunca se muestran teléfono ni correo. Cada conversación pertenece a una
  publicación o reclamo y solo la ven sus participantes.
- El personal de biblioteca solo ve las conversaciones de los reclamos que
  gestiona, y todo queda auditado.
- Se puede bloquear y reportar desde el chat. Al recuperarse el objeto, la
  conversación pasa a solo lectura.
- Hay límites de envío y de tamaño de mensaje.

### 7.4 Foto de perfil

La tabla `usuarios` no tiene ese campo y el backlog no tiene la historia (H64).
Backend (4 h): columna `foto_perfil_url`, migración idempotente y
`POST` y `DELETE /api/perfil/foto`, con los límites de las fotos de publicaciones
(JPEG o PNG de hasta 5 MiB). Web (3 h) y Android (5 h), que reutiliza la cámara
y la galería de H52 y Coil. Recomendación de privacidad: visible solo para el
propio usuario y para quien conversa con él, no en las publicaciones públicas.
Hay que confirmarlo por la Ley 29733.

## 8. Cierre del proyecto

Definición de terminado, igual que en el Sprint 3:

- Las tareas hechas y el criterio «Comprobar» demostrado con la API real y una
  MySQL de QA, sin mocks.
- Pruebas automáticas de cada capa en verde, y compilación y APK sin errores.
- Casos de prueba, evidencia y documento de entrega por integrante en `docs/`.
- Un commit por historia, con el formato `Hxx: acción breve`, en español.
- Migraciones idempotentes con respaldo. No se usa `docker compose down -v` ni se
  reimporta `script.sql` o `seed.sql` sobre datos reales.
- Ninguna clave ni JWT en Git, y nada se publica en `main` sin autorización.

Demo conjunta de cierre: voz, asistente, reclamo, aceptación, QR, entrega con el
escáner, conversación por chat con la biblioteca y tablero actualizado. En
Android se muestran también la ubicación con mapa, el push con la app cerrada,
Firebase Auth y una página dentro de la app con WebView.

Lo que no entre en el sprint se declara fuera del alcance del proyecto, con su
motivo. No se presenta como terminado.

## 9. Decisiones que necesita el equipo

1. **Android: 63 h contra 57 h.** Faltan 6 h para lo que debe entrar. Opciones:
   (a) dar más capacidad a Android o alargar el sprint; (b) mover el chat de
   Android (H55 y H56, 13 h) a «Debería», con lo que el chat queda en backend y
   web; (c) quitar el mapa de Google (2.5 h), que no alcanza solo y deja sin
   Google Cloud la semana 13. Se recomienda (a) o (b).
2. **Backend sin dueño: 16 h de «Debería» y 12 h de «Si sobra».** Yajaira tiene
   unas 32 h libres. Opciones: (a) que Yajaira haga el backend de H63 a H67, como
   hizo en el Sprint 3 con sus historias, aunque rompe «Yajaira solo front»;
   (b) dejar esas historias fuera del proyecto; (c) más capacidad para Leonel.
3. ¿Cuándo empieza y termina el Sprint 4, y cuál es la capacidad de cada uno?
4. ¿Firebase Auth entra como pide el curso? Se propone con token personalizado,
   sin cambiar las cuentas existentes. Hay que confirmarlo con el docente.
5. **Google Maps necesita un proyecto de Google Cloud con una clave** y puede
   exigir facturación habilitada. ¿Hay una cuenta del equipo? Si no, se puede usar
   un mapa de OpenStreetMap, pero no cuenta como Google Cloud.
6. ¿Quién hace el contrato y la API del chat? Se propone a Leonel.
7. ¿La foto de perfil se muestra solo a quien conversa con la persona, o también
   en las publicaciones?
8. La 15.2 pide publicar promociones en redes sociales. ¿Basta con preparar el
   texto y el enlace y registrar la publicación, sin conectar las APIs de las
   redes?
9. ¿Las dos categorías con sentido a quitar son «Accesorios QA semanal» y
   «Accesorios QA React»?
10. ¿Cuál es el estado del Sprint 3 de Leonel y cuándo se lleva a `main`? No hay
    su entrega en el repositorio.
