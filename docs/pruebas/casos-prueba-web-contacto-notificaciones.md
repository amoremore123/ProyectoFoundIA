# Casos de prueba — Pantallas de Contacto y notificaciones (H18 y H20)

Frontend web: Yajaira (`frontend/web`). API: Amore (H18 contacto, H20 reclamo).
Pruebas automáticas: `frontend/usuario/src/pages/Detalle.test.jsx` y `Notificaciones.test.jsx`.

## Qué se corrigió en las pantallas

| Pide el PDF | Antes | Ahora |
| --- | --- | --- |
| Explicar qué acción genera cada aviso | Sin explicación | Texto en Detalle (contactar y reclamar) y en Notificaciones; cada aviso muestra su tipo ("Solicitud de contacto", "Reclamo") |
| Falta de sesión | El botón desaparecía sin explicar | "Inicia sesión para reclamar este objeto" con enlace |
| Fallo y reintento del contacto | Error lejos de la tarjeta | Error en la tarjeta de esa coincidencia y botón "Reintentar contacto" |
| Confirmación solo cuando el servidor acepta | Correcto | Se mantiene; además el reclamo pide confirmación antes de avisar a otra persona |
| Marcar como leída y conservar el estado | Correcto, pero la campana no bajaba hasta recargar | La campana se actualiza al instante |
| Lista vacía y fallo de consulta | Un mismo aviso con "Reintentar" para todo | Fallo de carga con "Reintentar"; fallo al marcar sin "Reintentar" ni éxito ficticio |

También se corrigió el modal de confirmación (Mis publicaciones, reclamo y panel admin):
se dibuja en `<body>` (portal) porque la animación de aparición de la página lo
dejaba tapado por la barra superior en el móvil.

## Pruebas automáticas

| ID | Caso | Resultado esperado |
| --- | --- | --- |
| CP-W18-01 | Contacto válido desde una coincidencia propia | "Solicitud de contacto enviada."; sin correos en pantalla |
| CP-W18-02 | Visitante sin sesión | No ve "Contactar" |
| CP-W18-03 | Fallo y reintento | Error dentro de la tarjeta; "Reintentar contacto" envía y confirma |
| CP-W18-04 | Explicación del contacto | "quien lo encontró recibirá un aviso…" |
| CP-W18-05 | Contacto repetido | "Ya habías enviado una solicitud de contacto." |
| CP-W20-01 | Reclamo válido | Pide confirmación; después "Reclamo enviado…" |
| CP-W20-02 | Cancelar la confirmación | No se envía el reclamo |
| CP-W20-03 | Reclamo rechazado y reintento | Mensaje del servidor, sin éxito ficticio; el reintento envía |
| CP-W20-04 | Sin sesión | Sin botón; enlace "Inicia sesión para reclamar este objeto" |
| CP-W20-05 | Quien publicó el hallazgo | No ve la opción de reclamarlo |
| CP-WN-01 | Tipos, contador y explicación | "Reclamo", "Solicitud de contacto", "1 sin leer", sin correos |
| CP-WN-02 | Abrir un aviso | Se marca como leído y sigue leído al consultar de nuevo |
| CP-WN-03 | Aviso ya leído | No vuelve a llamar a la API |
| CP-WN-04 | Fallo al marcar | Sigue sin leer, sin "Reintentar" ni éxito ficticio |
| CP-WN-05 | Lista vacía | "No tienes notificaciones todavía." |
| CP-WN-06 | Fallo de consulta | Distinto de la lista vacía; "Reintentar" recupera |

Suite frontend: **135 pruebas, 0 fallos**.

## Punta a punta en el navegador (Docker) — 20 de octubre de 2026

Dos cuentas QA temporales: Luis (perdió una mochila) y Ana (encontró una parecida).
Vista a 375 px. Al terminar se eliminaron cuentas, publicaciones, coincidencia,
contacto, reclamo y avisos (la base volvió a 4 usuarios y 6 objetos).

| Paso | Resultado |
| --- | --- |
| Luis abre su publicación perdida | Ve la explicación y "Contactar" en cada coincidencia |
| Luis contacta la coincidencia de Ana | "Solicitud de contacto enviada." solo en esa tarjeta |
| Luis abre el hallazgo de Ana y pulsa "Reclamar" | Modal completo y centrado, foco en "Cancelar" |
| Luis confirma | "Reclamo enviado. Quien lo publicó recibió un aviso…" |
| Ana inicia sesión | Campana con 2 |
| Ana abre Notificaciones | Explicación, "2 sin leer", tipos "Reclamo" y "Solicitud de contacto", sin datos de Luis |
| Ana abre el reclamo | Pasa a leído; la campana baja a 1 al instante |
| Ana recarga | Sigue "1 sin leer" y el reclamo "Leída" |
