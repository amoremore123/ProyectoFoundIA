# Leonel — API/datos de sus ocho historias

Alcance exclusivo: **H21, H22, H23, H24, H26, H27, H28 y H29**.
Se implementan API/datos y pruebas que el PDF asigna a Leonel. No se adelantan
historias de otras personas. Amore continúa con móvil; Yajaira con web.
No se modifica main: Leonel revisará el sprint integrado al final.

## Contratos

Spring Boot `:8081`, API común para web/móvil:

- H21: POST `/api/coincidencias/{perdidoId}-{encontradoId}/descartar`.
  Solo titular del perdido. Repetir no duplica ni revive la pareja. Ver [contrato](endpoints.md).
- H22: POST `/api/coincidencias/{pareja}/confirmar`, cuerpo
  `{"confirmarRecuperacion":true}`. Guarda aceptación y recuperación propia de
  forma atómica, con fecha UTC. No modifica el hallazgo ajeno.
- H23: GET `/api/notificaciones`, PUT `/api/notificaciones/{id}/leida`.
  Aviso único por pareja nueva al titular activo/verificado del perdido.
  `enlace` apunta a `/coincidencias/{pareja}`; el destino revalida disponibilidad.
  Consultar/recalcular por GET no escribe avisos ni coincidencias.
- H24: GET/PUT `/api/notificaciones/preferencias`. Solo la cuenta del JWT.
  Cuerpo exacto de valores: `{"coincidencias":true,"contactos":true,"reclamos":true}`.
  Los tres booleanos son obligatorios. GET no crea filas; cambiar preferencias
  solo afecta eventos nuevos y conserva el historial. No se añaden tipos futuros.
- H27: POST `/api/objetos/{id}/reportes`. Ver [motivos, repetición y privacidad](reportar-publicacion-sprint2.md).

Django `:8000`, solo ADMIN activo:

- H26: [gestión y desactivación lógica de categorías](categorias-sprint2.md).
- H28: [consulta y resolución auditada de reportes](resolucion-reportes-sprint2.md).
- H29: [conteos del tablero con rango de fechas](metricas-sprint2.md).

La identidad se obtiene del JWT, no de IDs enviados en el cuerpo. Los clientes
deben permitir cancelar antes de enviar y confirmar éxito solo tras la respuesta
real. Manejar 400/401/403/404/409 sin ocultar errores ni mostrar éxito anticipado.

## Base de datos y pruebas

Cinco migraciones incrementales: H22 (fecha), H23 (avisos y eventos), H24
(preferencias) y H28 (auditoría). Revisar respaldo y autorizar destino antes de
aplicarlas a la base compartida. **No reimportar script.sql/seed.sql ni usar down -v**.

Pruebas: Spring con H2 temporal y Django con SQLite en memoria; MySQL solo en
instancia nueva aislada con guarda, nunca con la conexión habitual de la aplicación.
Las cifras y el estado real de validación se documentan en la entrega a Amore.
Esta entrega no acredita las pantallas web, APK o integración final que realizan
después las otras responsables.
