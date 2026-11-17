# Planificación del Sprint 4: cierre del proyecto

Borrador del 17/11/2026 para el Planning Poker. El Sprint 4 es el último: reúne
las historias del backlog que faltan y lo que quedó atrás en móvil. Las horas
son una estimación de API, web y Android que hay que confirmar con el equipo.
Nada de esto está implementado todavía.

Fuentes: el backlog (Word del equipo, 80 historias en 15 épicas), los repartos
de los Sprints 1, 2 y 3 y el documento de pendientes del Sprint 2 (PDF), la
entrega de Yajaira del Sprint 3 (13/11) y la
[entrega de Amore del Sprint 3](../integracion/entrega-amore-sprint3.md). Los
PDF y el Word no están en el repositorio.

## 1. Resumen

- **Estado del backlog.** De las 80 historias, 58 están hechas, 10 quedaron a
  medias (seis por la capa móvil) y 12 están sin empezar. Las 12 pendientes
  suman 72 puntos y las 10 a medias, 47.
- **Lo que más pesa.** El chat interno (Épica 9) no existe: no hay tablas ni
  rutas. También faltan las campañas de difusión (Épica 15), las notificaciones
  masivas, el modo sin conexión y parte de los respaldos.
- **Cuello de botella.** Android. Lo que debe entrar en móvil ocupa las 57 h de
  Amore sin margen. Lo que viene después no cabe si el Sprint 4 dura lo mismo que
  el Sprint 3.
- **Cierre.** Además de las historias, el sprint cierra el proyecto: integración
  en `main` con autorización, despliegue con HTTPS, pruebas de punta a punta,
  documentación final, video de respaldo y ensayo con el jurado.

## 2. Cómo llegamos al Sprint 4

| Sprint | Alcance | Resultado |
| --- | --- | --- |
| 1 | H1 a H20. El 2/10 eran siete historias (H11, H12, H1, H3, H4, H6 y H7). El 13/10 se sumaron las trece restantes | API y web terminadas. En móvil quedó una base pendiente: H8 a H10, la edición y eliminación de publicaciones, la cámara y el reclamo. La cámara y el reclamo se hicieron en el Sprint 3; las otras siguen pendientes |
| 2 | H21 a H40. Se tomaron 14 historias | Entregadas las 14. Pasaron al Sprint 3 H25, H32, H36, H37, H38 y H40 |
| 3 | 20 historias: las 6 arrastradas y las nuevas H41 a H54 | Terminadas según las entregas de Yajaira y de Amore. Faltan las pruebas físicas de Amore y llevar el sprint a `main` |
| 4 | Las historias restantes y el cierre del proyecto | Este documento |

Las historias H41 a H54 (custodia, IA, accesibilidad y otras) no están en el
backlog en Word, así que no cuentan en el balance de la siguiente sección.

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

## 4. Historias del Sprint 4

Los números H55 en adelante son provisionales. Las horas son API, web y Android
en ese orden. Los puntos son los del backlog. Donde la historia es nueva, es una
propuesta.

### Debe entrar

| ID | Historia | Backlog | Pts | API | Web | Android | API la hace |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H55 | Conversar por chat interno y ver mis conversaciones | 9.1 y 9.2 | 18 | 10 | 8 | 10 | Leonel |
| H56 | Bloquear o reportar desde el chat | 9.4 | 3 | 3 | 2 | 3 | Leonel |
| H57 | Aviso de mensaje nuevo con push | 5.3 | 5 | 3 | 1 | 6 | Leonel |
| H58 | Recuperar los datos después de una falla | 14.2 | 8 | 6 | 1 | 0 | Leonel |
| H59 | Respaldar también las fotos | 14.4 | 5 | 4 | 0 | 0 | Leonel |
| H60 | Campañas de difusión | 15.1 | 5 | 3 | 3 | 0 | Yajaira |
| M1 | Correcciones de móvil: teclado, estado en custodia y categorías | Varias | | 1 | 0 | 6 | Amore |
| M2 | Pruebas físicas del Sprint 3: H44, H46, H50, H51 y H52 | Sprint 3 | | 0 | 0 | 8 | |
| M3 | Búsqueda avanzada en móvil | 2.3 a 2.5 | 13 | 0 | 0 | 6 | |
| M4 | Ubicación con GPS y lugares del campus en móvil | 1.3 | 3 | 0 | 0 | 7 | |
| M5 | Editar y eliminar mis publicaciones en móvil | 1.5 | 5 | 0 | 0 | 5 | |
| | **Subtotal** | | | **30** | **15** | **51** | |

### Debería entrar

| ID | Historia | Backlog | Pts | API | Web | Android | API la hace |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H61 | Chat en solo lectura al recuperar el objeto | 9.5 | 3 | 1 | 1 | 1.5 | Leonel |
| H62 | Fotos en el chat | 9.3 | 5 | 3 | 2 | 4 | Leonel |
| H63 | Chat con la biblioteca por reclamo | Nueva | 8 | 4 | 4 | 5 | Leonel |
| H64 | Foto de perfil | Nueva | 5 | 4 | 3 | 5 | Yajaira |
| H65 | Sincronización y modo sin conexión | 13.3 | 8 | 1 | 0 | 10 | Amore |
| H66 | Promociones en redes sociales | 15.2 | 5 | 2 | 3 | 0 | Yajaira |
| H67 | Medir el alcance de las campañas | 15.3 | 8 | 5 | 4 | 0 | Yajaira |
| | **Subtotal** | | | **20** | **17** | **25.5** | |

### Si sobra tiempo

| ID | Historia | Backlog | Pts | API | Web | Android | API la hace |
| --- | --- | --- | --- | --- | --- | --- | --- |
| H68 | Elegir el canal de avisos | 5.5 | 3 | 3 | 1 | 1 | Leonel |
| H69 | Notificaciones masivas | 11.5 | 7 | 4 | 3 | 1 | Leonel |
| H70 | Restaurar una versión anterior | 14.6 | 5 | 3 | 2 | 0 | Leonel |
| H71 | Avisos destacados | 15.5 | 5 | 2 | 3 | 2 | Yajaira |
| M6 | Búsqueda por foto en móvil | Paridad | | 0 | 0 | 4 | |
| | **Subtotal** | | | **12** | **9** | **8** | |

La 13.3 es de prioridad Alta en el backlog y aquí está en «Debería» solo por la
capacidad de Android. La decisión 2 de la sección 8 pregunta si se sube.

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

| Integrante | Debe entrar | Debería entrar | Si sobra | Capacidad |
| --- | --- | --- | --- | --- |
| Leonel (API y cierre) | 43 h | +8 h | +10 h | 58 h |
| Yajaira (web, su API y cierre) | 29 h | +28 h | +11 h | 60.5 h |
| Amore (Android, cierre y 1 h de datos) | 57 h | +26.5 h | +8 h | 57 h |

Leonel y Yajaira terminan lo que «debe entrar» y lo de «debería» dentro de su
capacidad. Amore llena sus 57 h solo con lo que debe entrar. Los 34.5 h de
Android que vienen después no caben, salvo que el sprint sea más largo, que
alguien ayude con Android o que se recorte.

## 5. La parte de Amore: Android

### 5.1 Lo que se vio en las capturas

| Captura | Qué pasa | Causa encontrada | Tarea |
| --- | --- | --- | --- |
| Inicio de sesión con el teclado | Queda un hueco azul entre el formulario y el teclado, y el campo de la contraseña se corta | Hipótesis a comprobar: el manifiesto usa `adjustResize` y las pantallas añaden `imePadding()`, así que el teclado se compensa dos veces | M1 |
| Detalle de un objeto en custodia | Se ve el texto técnico `EN_CUSTODIA` junto a la etiqueta y aparece «No se pudieron actualizar las coincidencias» | Comprobado: `GET /api/objetos/{id}/coincidencias` responde 404 a todos los usuarios para un objeto en custodia, porque no es público, y la app lo muestra como error. La web muestra «En custodia» | M1 |
| Categorías al publicar | Hay dos que no tienen sentido | «Accesorios QA semanal» (id 9) y «Accesorios QA React» (id 10) son datos de pruebas del panel. No están en `database/seed.sql` ni en el código | M1 |
| Registrar entrega con error genérico | «No se pudo completar la operación» | Corregido el 17/11 en el commit `e5ec7e3`: validación del QR escaneado y mensajes reales de Django. Falta repetir la prueba con el teléfono | M2 |
| «No hay conexión con FoundIA» | La app no llega al servidor | No es un fallo de la app. Faltaban los túneles USB; ya los mantiene el vigilante de `qa/telefono` | Ninguna |

Se asume que las dos categorías que no tienen sentido son las dos «Accesorios
QA». Hay que confirmarlo.

### 5.2 Paridad con la web

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

Tareas de M3, M4 y M5:

- **M3 (6 h).** Ampliar el servicio Retrofit y el repositorio, y añadir
  ubicación, fechas, radio y chips de filtros activos. No cambia la API.
- **M4 (7 h).** Permiso de ubicación, «Usar mi ubicación» con lugar y
  coordenadas, y los lugares del campus como en la web: Biblioteca, Cafetería,
  Laboratorios, Talleres, Aulas y Auditorio. Si se niega el permiso, se escribe.
  El detalle muestra el lugar con un enlace a mapas.
- **M5 (5 h).** Editar y eliminar desde Mis reportes, con confirmación. No se
  edita lo que está en custodia o recuperado, igual que la web.

M1 son 6 h de Android: el teclado (2 h), el estado en custodia con el texto «En
custodia» en un solo lugar y sin pedir coincidencias a un objeto en custodia
(3 h) y ordenar las categorías (1 h). La desactivación de las dos categorías de
prueba es 1 h de datos, desde el panel de administración.

M2 son 8 h para cerrar el Sprint 3 con pruebas físicas: H44 (etiqueta y QR:
válido, usado, de otro objeto y vencido), H46 (dictado con ruido), H50 (aviso
con la app cerrada), H51 (TalkBack y texto al 130 %) y H52 (cámara real).

### 5.3 Mensajería: ¿tiene sentido?

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

Propuesta técnica:

- Tablas: `conversaciones`, `participantes`, `mensajes` y `bloqueos_chat`, con
  migración idempotente y respaldo.
- Rutas: `GET /api/conversaciones`, `GET /api/conversaciones/{id}/mensajes?despuesDe=`,
  `POST /api/conversaciones/{id}/mensajes`, `POST /api/conversaciones/{id}/leer` y
  `POST /api/usuarios/{id}/bloqueo`.
- Primera fase: consulta cada 5 a 10 s con el chat abierto, con el cursor
  `despuesDe` de H50.
- Aviso con la app cerrada: Firebase Cloud Messaging (H57). En la prueba de H50
  Android retrasó el trabajo periódico de WorkManager, que no sirve para un chat.

### 5.4 Foto de perfil

La tabla `usuarios` no tiene ese campo y el backlog no tiene la historia (H64).

- **API (4 h).** Columna `foto_perfil_url`, migración idempotente,
  `POST /api/perfil/foto` y `DELETE /api/perfil/foto`, con los mismos límites que
  las fotos de publicaciones: JPEG o PNG de hasta 5 MiB. Reduce la imagen en el
  servidor.
- **Web (3 h) y Android (5 h).** En Android se reutiliza la cámara y la galería
  de H52 y Coil. Se muestra en Perfil, en el inicio y en el chat, y se puede
  quitar.
- **Privacidad.** Recomendación: visible solo para el propio usuario y para quien
  conversa con él, no en las publicaciones públicas. Hay que confirmarlo por la
  Ley 29733.

### 5.5 Orden de trabajo y qué se recorta primero

- **Bloque 1.** M2 primero, porque hay que cerrar el Sprint 3 con las pruebas
  físicas. Después M1, M3, M4 y M5. Mientras, Leonel publica el contrato del chat.
- **Bloque 2.** El chat (H55 y H56), el aviso con push (H57) y el cierre
  (C3 y C4), cuando la API esté lista.
- **Si Android se atrasa, se recorta en este orden:** primero el push de H57
  (se deja la consulta periódica), después M3. No se recorta H56: sin bloquear y
  reportar no debe salir el chat.

## 6. Cierre del proyecto

Definición de terminado, igual que en el Sprint 3:

- Las seis tareas hechas y el criterio «Comprobar» demostrado con la API real y
  una MySQL de QA, sin mocks.
- Pruebas automáticas de cada capa en verde, y compilación y APK sin errores.
- Casos de prueba, evidencia y documento de entrega por integrante en `docs/`.
- Un commit por historia, con el formato `Hxx: acción breve`, en español.
- Migraciones idempotentes con respaldo. No se usa `docker compose down -v` ni se
  reimporta `script.sql` o `seed.sql` sobre datos reales.
- Ninguna clave ni JWT en Git, y nada se publica en `main` sin autorización.

Demo conjunta de cierre: voz, asistente, reclamo, aceptación, QR, entrega con el
escáner, conversación por chat con la biblioteca y tablero actualizado.

Lo que no entre en el sprint se declara fuera del alcance del proyecto, con su
motivo. No se presenta como terminado.

## 7. Riesgos

| Riesgo | Respuesta |
| --- | --- |
| La API del chat no está lista a mitad del sprint | Leonel publica el contrato el primer día. Android avanza antes con M1 a M5 |
| Android no alcanza para todo | Se recorta en el orden de la sección 5.5, o se acuerda más capacidad |
| Sin push, el chat se siente lento | H57 con Firebase Cloud Messaging. Hasta entonces, consulta cada 5 a 10 s con el chat abierto |
| Los datos de otra persona (foto y nombre) | Foto visible solo en la conversación. Sin teléfono ni correo en ninguna pantalla |
| Las pruebas físicas siguen pendientes | Se hacen primero (M2) y se registran antes de empezar lo demás |
| Las fotos se pierden si falla el volumen | H59 las incluye en el respaldo |
| Cambiar de rama con el IDE abierto dañó el índice | Cerrar la sincronización antes de cambiar de rama y usar el Gradle del proyecto |

## 8. Decisiones que necesita el equipo

1. ¿Cuándo empieza y termina el Sprint 4, y cuál es la capacidad de cada uno?
2. La 13.3 es de prioridad Alta y está en «Debería» por la capacidad de Android.
   ¿Se sube a «Debe» a costa de M4 o M5, o se queda?
3. ¿Quién hace la API del chat? Se propone a Leonel.
4. ¿Se activa Firebase Cloud Messaging? ¿Firebase Auth se hace o se descarta?
   Hay una propuesta en `movil/android/docs/PREPARACION_SPRINT_3.md` y no hay nada
   implementado.
5. ¿La foto de perfil se muestra solo a quien conversa con la persona, o también
   en las publicaciones?
6. La 15.2 pide publicar promociones en redes sociales. ¿Basta con preparar el
   texto y el enlace y registrar la publicación, sin conectar las APIs de las
   redes?
7. ¿Las dos categorías con sentido a quitar son «Accesorios QA semanal» y
   «Accesorios QA React»?
8. ¿Cuál es el estado del Sprint 3 de Leonel y cuándo se lleva a `main`? No hay su
   entrega en el repositorio.
