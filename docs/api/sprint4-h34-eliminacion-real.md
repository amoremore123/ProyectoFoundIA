# H34 — Eliminación real de la cuenta (derecho de cancelación)

Responsable del backend: Leonel (Django para atender la solicitud, Spring para borrar los archivos). Web:
Yajaira (pantalla del panel). Android: Amore (sin cambios: la persona ya puede pedirla). Pedido por el equipo el
23/11/2026 para cumplir la **Ley 29733** (derecho de cancelación). No estaba en el plan del Sprint 4.

> **Aprobado por el equipo el 23/11/2026**: las cinco decisiones del final, tal como se recomendaron.

## Qué existe hoy

- La persona pide la eliminación desde su perfil (`POST /api/perfil/solicitud-eliminacion`, Spring) y ve el
  estado. **No se borra nada**: la solicitud queda `PENDIENTE` para siempre. El mensaje lo dice: «No se han
  eliminado tu cuenta ni tus datos».
- Nadie puede atenderla: no hay ruta en el panel.

## Idea general: anonimizar, no borrar la fila

La cuenta **no se puede borrar de la tabla**: la referencian los reclamos, contactos, custodias, disposiciones
y reportes, varios de ellos registros que la biblioteca debe conservar (por ejemplo, quién recibió un objeto en
custodia). Por eso:

- Se **borran** los datos que solo sirven a la persona (foto, dispositivos, avisos, preferencias, etc.).
- Se **anonimiza** la cuenta: ya no tiene nombre, correo ni contraseña que identifiquen a la persona, y no se
  puede volver a usar.
- Se **conservan** los registros que pertenecen a otros o a la biblioteca, pero ahora apuntan a «Cuenta
  eliminada», y se limpia el texto libre que pudo escribir la persona.

## Qué pasa con cada dato al atender la solicitud

Todo en **una sola transacción**: o se hace completo o no se hace nada.

| Dato | Qué se hace |
| --- | --- |
| **Cuenta** (`usuarios`) | `nombre` = «Cuenta eliminada», `apellido` vacío, `correo` = `eliminada-{id}@foundia.invalid`, contraseña imposible de usar, `estado` = `ELIMINADO` (nuevo), sin códigos de verificación ni bloqueos, sin foto. El correo original queda libre para registrarse de nuevo |
| Sesiones | Se cierran todas (`sesion_version` + 1) y se revoca Firebase (H72) |
| Foto de perfil (H64) | Se borra el archivo |
| Dispositivos (H57), avisos (`notificaciones`), preferencias (H24/H68), tokens de recuperación (H35) y revocados (H37), lecturas y bloqueos del chat | Se **borran** |
| **Publicaciones** propias activas u ocultas | Pasan a `ELIMINADO`; se borran sus fotos (archivos y filas), la `descripcion` pasa a «[eliminada a pedido de la persona]», y se quitan `ubicacion`, `latitud` y `longitud`. Dejan de verse en búsquedas y coincidencias |
| Publicaciones `EN_CUSTODIA` o `RECUPERADO` | Se **conservan** (son registro de la biblioteca) con el autor anonimizado; se quitan la ubicación y las coordenadas |
| Mensajes del chat que escribió (H55/H62) | El texto pasa a «Mensaje eliminado» y se borra la foto (decisión 3) |
| Conversaciones | Quedan en solo lectura; la otra persona ve «Cuenta eliminada» |
| Reclamos propios `PENDIENTE` o `ACEPTADO` | Pasan a `RETIRADO` (no puede ir a retirar el objeto) y se borra `respuesta_verificacion` |
| Reclamos cerrados, contactos y reportes que hizo | Se conservan, sin el texto libre que escribió (`mensaje` del contacto y `descripcion` del reporte pasan a «[eliminado]») |
| Custodias, disposiciones y resoluciones hechas por personal | No aplica: ver regla de cuentas del personal |
| Aceptación de los documentos legales | Se conserva la versión y la fecha (prueba del consentimiento, sin datos de la persona) |
| Solicitud de eliminación | Pasa a `ATENDIDA`, con quién la atendió y cuándo |

### Archivos (fotos)

Django no tiene acceso al volumen de fotos; solo Spring. Al atender, Django anota las rutas a borrar en una tabla
nueva `archivos_por_borrar`, en la misma transacción. Un proceso de Spring las borra **cada minuto** y quita la
fila. Si un archivo ya no existe, igual se da por borrado.

## Reglas

1. Solo se atienden cuentas con rol `USUARIO`. Una cuenta `BIBLIOTECA` o `ADMIN` firmó custodias y
   resoluciones: primero hay que cambiarle el rol (H36). Si no, `409`.
2. Un administrador no puede atender su propia solicitud (`409`).
3. Atender pide **confirmación**: escribir el correo de la cuenta (`"confirmacion": "<correo>"`). Si no coincide,
   `400` y no se hace nada. No se puede deshacer.
4. Se puede **rechazar** con un motivo (por ejemplo, si la solicitud no es de la persona). La cuenta no cambia.
5. Al atender, se envía un **correo de confirmación** a la dirección original (se guarda antes de anonimizarla y
   se envía cuando la transacción ya se confirmó, para no avisar algo que no ocurrió). Si el correo falla, la
   eliminación queda hecha igual y la respuesta lo indica (`correoEnviado: false`).
6. Plazo (decisión 5): el Reglamento de la Ley 29733 (D.S. 003-2013-JUS) da **10 días hábiles** para atender la
   cancelación. El panel muestra la fecha límite y las solicitudes vencidas primero. Confirmar con el docente.

## Rutas (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/solicitudes-eliminacion/?estado=PENDIENTE\|ATENDIDA\|RECHAZADA` | Lista, las pendientes más antiguas primero; con la fecha límite |
| `GET` | `/api/admin/solicitudes-eliminacion/{id}/` | Detalle: qué se va a borrar, anonimizar y conservar (conteos), antes de atender |
| `POST` | `/api/admin/solicitudes-eliminacion/{id}/atender/` | `{"confirmacion": "<correo de la cuenta>"}` → `200` con el resumen de lo hecho |
| `POST` | `/api/admin/solicitudes-eliminacion/{id}/rechazar/` | `{"motivo": "..."}` (3 a 500 caracteres) → `200` |

Errores: `400` (confirmación que no coincide, motivo inválido), `401`, `403`, `404`, `409` (ya atendida o
rechazada, cuenta del personal, solicitud propia).

En Spring no cambia la ruta de la persona; solo el texto informativo, que deja de decir que no se borra nada, y
el estado muestra `ATENDIDA` o `RECHAZADA` (con el motivo).

## Base de datos

`database/migracion_h34_eliminacion_real.sql` (idempotente, también en `script.sql`):

- `usuarios.estado` admite `ELIMINADO` (también en Spring y Django).
- `solicitudes_eliminacion`: `atendida_por_id`, `fecha_atencion`, `motivo_rechazo`; `estado` admite `ATENDIDA` y
  `RECHAZADA`.
- Tabla `archivos_por_borrar (id, ruta, anotado_en)`.

## Límites (declarados)

- **Respaldos:** los datos siguen en los respaldos de la base y de fotos hasta que la retención los borra
  (7 días por defecto).
- **Restaurar una versión anterior (H70, decisión 4):** `restaurar_version.sh` conserva la tabla
  `solicitudes_eliminacion` como está (igual que el historial de respaldos) y, al terminar, pide ejecutar
  `python manage.py reaplicar_eliminaciones` en el panel, que vuelve a anonimizar las cuentas de las solicitudes
  `ATENDIDA` que hayan vuelto. Es idempotente. Mientras no se ejecute, la lista del panel marca
  `pendientesDeReaplicar`.
- Los correos ya enviados y los avisos push ya entregados no se pueden borrar.
- Lo que la otra persona copió o capturó de un chat queda fuera del sistema.

## Decisiones del equipo (aprobadas el 23/11/2026)

1. ¿Están de acuerdo con **anonimizar en vez de borrar la fila** (por las custodias y reclamos que deben quedar)?
2. ¿Las publicaciones en custodia o recuperadas se **conservan** con el autor anonimizado?
3. Mensajes del chat: ¿se reemplaza el texto por «Mensaje eliminado» (se recomienda) o se conservan para la otra
   persona?
4. ¿Se ajusta H70 para que una restauración no reviva cuentas eliminadas?
5. ¿El plazo de 10 días hábiles es el que pide el curso?

## Pruebas que se deben ejecutar y registrar

- Atender: cada fila de la tabla de arriba queda como dice (borrada, anonimizada o conservada), todo en una
  transacción (si algo falla, nada cambia); la persona no puede iniciar sesión ni con la sesión anterior; el
  correo original se puede volver a registrar; la otra persona del chat ve «Cuenta eliminada».
- Archivos: Spring borra las fotos anotadas (perfil, publicaciones y chat) y vacía la tabla.
- Reglas: confirmación incorrecta `400` sin cambios; personal o solicitud propia `409`; ya atendida `409`;
  rechazar con motivo; `401`, `403`, `404`.
- Correo de confirmación a la dirección original (Mailpit).
- Con MySQL real: la migración dos veces y el flujo completo sobre una cuenta con publicaciones, fotos, chat,
  reclamo y avisos.

## Verificación del 23/11/2026

- Django: 15 pruebas nuevas en `test_sprint4_h34.py` (lista y vista previa sin cambios; cuenta anonimizada con
  sesiones cerradas y correo libre; lo que solo sirve a la persona se borra y las referencias a sus avisos se
  sueltan; publicaciones eliminadas o conservadas sin ubicación; chat, reclamos, contactos y reportes; fotos
  anotadas para Spring; correo a la dirección original; si el correo falla la eliminación queda hecha;
  confirmación incorrecta sin cambios; si algo falla a mitad, nada cambia y no sale el correo; personal,
  solicitud propia o ya atendida `409`; rechazar con motivo; plazo de 10 días hábiles y vencidas; permisos;
  reaplicar tras una restauración). Suite completa: **227 pruebas, OK**.
- Spring: 4 pruebas nuevas en `EliminacionCuentaIntegrationTest` (borra las fotos anotadas de perfil, chat y
  publicación sin tocar las demás; una ruta manipulada no borra nada fuera de lo permitido; una cuenta `ELIMINADO`
  se carga y no puede iniciar sesión; texto nuevo y motivo del rechazo). Suite completa: **601 pruebas, 0 fallos**.
- MySQL 8.0 de QA, con la cuenta real de Luis (foto de perfil, 7 publicaciones con 2 fotos, 5 mensajes y una foto
  del chat, 12 avisos y un reclamo pendiente):

| Caso | Resultado |
| --- | --- |
| Migración | `migracion_h34_eliminacion_real.sql` dos veces sin error |
| Luis pide la eliminación | `201`, `PENDIENTE`, con el texto nuevo |
| Panel: lista y vista previa | Fecha límite `2026-12-07`; 7 publicaciones, 2 fotos, foto de perfil, 5 mensajes, 1 foto del chat, 12 avisos, 1 reclamo |
| Confirmación con otro correo / correcta / otra vez | `400` / `200 ATENDIDA` con `correoEnviado: true` / `409` |
| Correo | Llegó a `luis@foundia.dev` en Mailpit: «FoundIA \| Tu cuenta fue eliminada» |
| Luis inicia sesión / usa su sesión anterior | `401` / `401` |
| Ana en la conversación | Ve «Cuenta eliminada», en solo lectura; los mensajes de Luis dicen «Mensaje eliminado»; la foto del chat `404` |
| Base | Cuenta `eliminada-3@foundia.invalid`, `ELIMINADO`, sin foto; 7 publicaciones `ELIMINADO` sin descripción ni ubicación y sin fotos; 0 avisos; reclamo `RETIRADO` sin respuesta; 2 conversaciones en solo lectura |
| Fotos | 4 anotadas; Spring las borró del volumen en menos de un minuto y vació la tabla. Una ruta de los datos de ejemplo (`/uploads/objetos/llavero.jpg`, que nunca tuvo archivo) se descartó como ruta no válida |
| Registrarse de nuevo con `luis@foundia.dev` | `201` |
| Restaurar la versión de antes (H70) | La cuenta volvió; la solicitud quedó `ATENDIDA`; el script avisó «la versión trajo de vuelta 1 cuenta(s)» con el comando |
| `reaplicar_eliminaciones` | «Cuentas anonimizadas de nuevo: 1»; sus fotos se anotaron y Spring las borró otra vez. Ejecutarlo de nuevo: 0 |
| Instalación nueva | `script.sql`, `seed.sql` y la migración sobre esa base, sin errores |

- **Falta en la web (Yajaira):** la pantalla del panel (lista con fecha límite, vista previa, atender con la
  confirmación del correo y rechazar con motivo). Android no necesita cambios.
- Al actualizar: aplicar la migración, reconstruir `api` y `admin-api` (el panel necesita `MAIL_HOST` para el
  correo; ya está en `docker-compose.yml`).
