# Planificación del Sprint 4: móvil (Amore)

Borrador del 17/11/2026 para llevar al Planning Poker. Las horas son una
estimación de la capa Android. Las de API y web son una referencia para
acordar con Leonel y Yajaira. Nada de esto está implementado todavía.

Fuentes: el backlog de historias (Word del equipo, 15 épicas), el reparto del
Sprint 3 (PDF del 30/10/2026), que no están en el repositorio, y la
[entrega de Amore del Sprint 3](../integracion/entrega-amore-sprint3.md).

## 1. Resumen

El Sprint 4 de móvil tiene tres bloques:

- **A. Cerrar el Sprint 3 y corregir lo que viste en las pruebas.** Teclado,
  estado en custodia, categorías y las pruebas físicas pendientes.
- **B. Que la app siga a la web.** La API ya ofrece casi todo lo que usa la web.
  Faltan la búsqueda avanzada, la ubicación con GPS, editar o eliminar mis
  publicaciones y la búsqueda por foto.
- **C. Funciones nuevas.** Mensajería (Épica 9 del backlog) y foto de perfil.

Capacidad de referencia: 57 h, la del Sprint 3 (6.3 h por día durante 9 días).
Hay que confirmar las fechas del Sprint 4.

| Prioridad | Horas Android | Contenido |
| --- | --- | --- |
| Debe entrar | 52.5 h | Bloque A completo, B1 a B3, chat de texto, bloquear y reportar, solo lectura, foto de perfil |
| Debería entrar | 15 h | Chat con la biblioteca, aviso de mensaje con push, búsqueda por foto |
| Si sobra tiempo | 30 h | Fotos en el chat, Firebase Auth, actualizar dependencias, HTTPS, deep link, modo sin conexión |

Lo que debe entrar deja 4.5 h de margen (8 %). El chat de Android depende de que
la API de mensajería esté lista a mitad del sprint.

## 2. Lo que se vio en las capturas

| Captura | Qué pasa | Causa encontrada | Tarea |
| --- | --- | --- | --- |
| Inicio de sesión con el teclado | Queda un hueco azul entre el formulario y el teclado, y el campo de la contraseña se corta | Hipótesis a comprobar: el manifiesto usa `adjustResize` y las pantallas añaden `imePadding()`, así que el teclado se compensa dos veces | A1 |
| «No hay conexión con FoundIA» | La app no llega al servidor | No es un fallo de la app. Faltaban los túneles USB del teléfono; ya los mantiene el vigilante de `qa/telefono` | Ninguna |
| Detalle de un objeto en custodia | Se ve el texto técnico `EN_CUSTODIA` junto a la etiqueta y aparece «No se pudieron actualizar las coincidencias» | Comprobado: `GET /api/objetos/{id}/coincidencias` responde 404 a todos los usuarios para un objeto en custodia, porque no es público, y la app lo muestra como error. La web muestra «En custodia» | A2 |
| Registrar entrega con error genérico | «No se pudo completar la operación» | Corregido el 17/11 en el commit `e5ec7e3`: validación del QR escaneado y mensajes reales de Django. Falta repetir la prueba con el teléfono | A4 |
| Categorías al publicar | Hay dos que no tienen sentido | «Accesorios QA semanal» (id 9) y «Accesorios QA React» (id 10) son datos de pruebas del panel. No están en `database/seed.sql` ni en el código | A3 |

Se asume que las dos categorías que mencionas son esas. Hay que confirmarlo.

## 3. Paridad con la web

Se comparó lo que llama la web de usuario con lo que llama la app.

| Función | Web | Móvil | Tarea |
| --- | --- | --- | --- |
| Registro, verificación, ingreso y recuperar contraseña | Sí | Sí | |
| Publicar con foto o cámara, y sugerencia de FINDI | Sí | Sí | |
| Coincidencias: ver, confirmar y descartar | Sí | Sí | |
| Reclamo en custodia, QR de retiro y escáner | Sí | Sí | |
| Avisos, historial y preferencias | Sí | Sí | |
| FINDI por texto y voz, reportar, compartir, invitar, eliminar cuenta | Sí | Sí | |
| Buscar por texto y categoría | Sí | Sí | |
| **Búsqueda avanzada**: ubicación, fechas desde y hasta, radio por cercanía, chips de filtros activos | Sí | No. La API ya admite `desde`, `hasta`, `latitud`, `longitud` y `radioKm`, pero la app solo envía `q`, `tipo`, `categoriaId` y `ubicacion`, y la pantalla ofrece solo texto y categoría | B1 |
| **Ubicación al publicar**: «Usar mi ubicación» con GPS y lugar, y coordenadas guardadas | Sí | No. Es un campo de texto libre | B2 |
| **Editar y eliminar mis publicaciones** | Sí | No hay pantalla. La API y el repositorio ya existen y no los usa ninguna pantalla | B3 |
| **Búsqueda por foto** | Sí | No | B4 |
| Voz de FINDI | Voz del servidor | Voz del dispositivo (TextToSpeech) | Diferencia aceptable |
| Verificación pública de la etiqueta | Página `/custodia/verificar` | La abre el navegador | B5 si sobra |
| Panel administrativo | Sí | No aplica | |

## 4. Mensajería

### ¿Tiene sentido?

Sí, pero conviene distinguir dos conversaciones y no mezclarlas.

1. **Entre personas.** Para un objeto que sigue con quien lo encontró (estado
   ACTIVO), una vez aceptado el contacto o el reclamo. Es la Épica 9.1 del
   backlog: coordinar la entrega sin compartir el número.
2. **Con la biblioteca.** Una conversación por reclamo en custodia. La
   biblioteca pide un detalle más para verificar, acuerda cuándo retirar y
   avisa que el objeto llegó. Encaja con el flujo actual, donde quien resuelve
   el reclamo es la biblioteca (H43) y no quien encontró el objeto. No está en
   el backlog y sería una historia nueva.

Lo que no tiene sentido es un chat libre entre quien encontró y quien reclama
cuando el objeto ya está en la biblioteca: quien lo encontró ya no lo tiene.
Por eso el flujo de custodia reemplazó a «Contactar» (H42).

### Reglas de privacidad

- Nunca se muestran teléfono ni correo. Cada conversación pertenece a una
  publicación o reclamo, y solo la ven sus participantes.
- El personal de biblioteca solo ve las conversaciones de los reclamos que
  gestiona. Todo queda auditado, igual que el resto de la custodia.
- Se puede bloquear y reportar desde el chat. Cuando el objeto se recupera, la
  conversación pasa a solo lectura (Épica 9.5).
- Límites de envío y de tamaño de mensaje, y avisos de privacidad como en la voz
  de FINDI.

### Propuesta técnica

Hoy no existe nada de esto en el backend: no hay tablas de conversaciones ni de
mensajes. Existen `contactos`, `notificaciones` y `preferencias_notificacion`,
que se pueden reutilizar.

- Tablas: `conversaciones` (tipo, objeto, reclamo, estado), `participantes`,
  `mensajes` y `bloqueos_chat`. Migración idempotente con respaldo, como en el
  Sprint 3.
- Rutas: `GET /api/conversaciones`, `GET /api/conversaciones/{id}/mensajes?despuesDe=`,
  `POST /api/conversaciones/{id}/mensajes`, `POST /api/conversaciones/{id}/leer`
  y `POST /api/usuarios/{id}/bloqueo`.
- **Primera fase, consulta cada 5 a 10 s con el chat abierto**, con el mismo
  cursor `despuesDe` de H50.
- **Aviso de mensaje con la app cerrada: Firebase Cloud Messaging.** En la
  prueba de H50 Android retrasó el trabajo periódico de WorkManager, que no
  sirve para un chat. Es el argumento principal para activar FCM en este sprint.

### Historias propuestas (numeración provisional, a confirmar con el equipo)

| ID | Historia | Backlog | Android | API | Web |
| --- | --- | --- | --- | --- | --- |
| H55 | Conversar y ver mis conversaciones | 9.1 y 9.2 | 10 h | 10 h | 8 h |
| H56 | Bloquear o reportar desde el chat | 9.4 | 3 h | 3 h | 2 h |
| H57 | Chat en solo lectura al recuperar el objeto | 9.5 | 1.5 h | 1 h | 1 h |
| H58 | Chat con la biblioteca por reclamo | Nueva | 5 h | 4 h | 4 h |
| H59 | Aviso de mensaje nuevo con push | 5.3 | 6 h | 3 h | 0 h |
| H60 | Fotos en el chat | 9.3 | 4 h | 3 h | 2 h |

## 5. Foto de perfil

Hoy la tabla `usuarios` no tiene ese campo y el backlog no tiene la historia.

- **API (referencia: 4 h).** Columna `foto_perfil_url`, migración idempotente,
  `POST /api/perfil/foto` y `DELETE /api/perfil/foto`. Mismos límites que las
  fotos de publicaciones: JPEG o PNG de hasta 5 MiB. Reduce la imagen en el
  servidor.
- **Web (referencia: 3 h)** y **Android (5 h).** En Android se reutiliza la
  cámara y la galería de H52 y Coil, que ya está en el proyecto. Se muestra
  en Perfil, en el inicio y en el chat, con la opción de quitarla.
- **Privacidad.** Recomendación: visible solo para el propio usuario y para
  quien conversa con él. No se muestra en las publicaciones públicas. Hay que
  confirmarlo con el equipo por la Ley 29733.
- **Moderación.** Se puede reportar con el mecanismo que ya existe.

Se hace antes del chat, que la usa en los avatares. Historia propuesta: **H61**.

## 6. Plan del sprint, capa Android

### Debe entrar: 52.5 h

| ID | Tarea | Backlog | Horas |
| --- | --- | --- | --- |
| A1 | Teclado en inicio de sesión y registro. Probar en el Moto G20 (Android 11), en el emulador y con texto al 130 % | Correcciones | 2 |
| A2 | Estado en custodia: texto «En custodia» en un solo lugar para todos los estados; no pedir coincidencias a un objeto en custodia ni a uno encontrado; quitar el aviso de error | Correcciones | 3 |
| A3 | Categorías: desactivar las dos de prueba desde el panel en la base de QA y revisar la de cada compañero (1 h de datos); ordenar la lista en la app y dejar «Otros» al final (1 h) | 6.1 | 2 |
| A4 | Pruebas físicas: H44 (escaneo de etiqueta y QR: válido, usado, otro objeto y vencido), H46 (dictado con ruido), H51 (TalkBack y texto al 130 %), H52 (cámara real) y H50 (aviso con la app cerrada). Con capturas, en `docs/pruebas/` | Sprint 3 | 8 |
| B1 | Búsqueda avanzada: ubicación, fechas, radio por cercanía y chips de filtros activos. Sin cambios en la API: se amplían el servicio Retrofit y el repositorio | 2.3, 2.4 y 2.5 (13 pts) | 6 |
| B2 | Ubicación al publicar: permiso, «Usar mi ubicación» con lugar y coordenadas, y lugares del campus como en la web (Biblioteca, Cafetería, Laboratorios, Talleres, Aulas y Auditorio). Si se niega el permiso, se escribe. Mostrar el lugar en el detalle con enlace a mapas | 1.3 | 7 |
| B3 | Editar y eliminar mis publicaciones, con confirmación. No se edita lo que está en custodia o recuperado (igual que la web) | 1.5 (5 pts) | 5 |
| C2 | Foto de perfil (H61) | Nueva | 5 |
| H55 | Chat: conversaciones y mensajes de texto | 9.1 y 9.2 (18 pts) | 10 |
| H56 | Bloquear o reportar desde el chat | 9.4 (3 pts) | 3 |
| H57 | Chat en solo lectura al recuperar el objeto | 9.5 (3 pts) | 1.5 |

### Debería entrar: 15 h

| ID | Tarea | Horas |
| --- | --- | --- |
| H58 | Chat con la biblioteca por reclamo, incluida la bandeja en el modo biblioteca | 5 |
| H59 | Aviso de mensaje nuevo con push (Firebase Cloud Messaging) | 6 |
| B4 | Búsqueda por foto, reutilizando la cámara de H52 | 4 |

### Si sobra tiempo: 30 h

| ID | Tarea | Horas |
| --- | --- | --- |
| H60 | Fotos en el chat | 4 |
| D1 | Firebase Auth. La propuesta está en `movil/android/docs/PREPARACION_SPRINT_3.md`. Incluye 2 h de análisis y no hay nada implementado | 7 |
| D2 | Actualizar dependencias y el plugin de Android (hay unas 20 versiones más nuevas) | 4 |
| D3 | HTTPS en la compilación de entrega: hoy `usesCleartextTraffic` está activado | 2 |
| B5 | Abrir la etiqueta de custodia directamente en la app | 3 |
| D4 | Modo sin conexión: cola de publicaciones (Épica 13.3). Room ya guarda la caché de lectura | 10 |

### Orden sugerido

- **Bloque 1.** A4 primero, porque hay que cerrar el Sprint 3 con las pruebas
  físicas. Después A1, A2, A3, B1 y B2.
- **Bloque 2.** B3, C2 y el chat (H55, H56 y H57), cuando la API esté lista.
- Si el chat se atrasa, se pasa a «Debería entrar» y se adelanta B4 y la
  actualización de dependencias.

## 7. Dependencias y riesgos

| Riesgo | Respuesta |
| --- | --- |
| La API de mensajería no está lista a mitad del sprint | Dejar A, B y C2 primero. El chat entra al final con consulta periódica |
| Sin push el chat se siente lento | H59 con Firebase Cloud Messaging. Hasta entonces, consulta cada 5 a 10 s con el chat abierto |
| Hay que mostrar datos de otra persona (foto y nombre) | Foto visible solo en la conversación; sin teléfono ni correo en ninguna pantalla |
| Las pruebas físicas siguen pendientes | Se hacen primero (A4) y se registran antes de empezar lo demás |
| Cambios entre ramas con el IDE abierto dejaron el índice dañado | Cerrar la sincronización antes de cambiar de rama y usar el Gradle del proyecto |

## 8. Decisiones que necesita el equipo

1. ¿El chat entre personas solo existe para objetos activos con contacto
   aceptado, y para objetos en custodia se conversa con la biblioteca?
2. ¿Quién hace la API de mensajería y la de foto de perfil? En el Sprint 3 cada
   persona hizo la API de sus historias.
3. ¿Se activa Firebase Cloud Messaging? ¿Firebase Auth se hace o se descarta?
4. ¿Dónde se muestra la foto de perfil?
5. ¿Las dos categorías con sentido a quitar son «Accesorios QA semanal» y
   «Accesorios QA React»?
6. ¿Cuándo empieza y termina el Sprint 4, y cuál es la capacidad de cada uno?
