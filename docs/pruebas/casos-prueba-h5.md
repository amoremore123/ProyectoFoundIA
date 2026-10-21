# H5 — Editar o eliminar una publicación propia

## Contrato

- `PUT /api/objetos/{id}` acepta el mismo `ObjetoRequest` que POST y mantiene las validaciones de H1/H3/H4.
- Solo el autor identificado por el JWT puede editar o eliminar. Otro usuario, una sesión ausente y ADMIN reciben rechazo; moderación ADMIN pertenece a H15.
- `DELETE /api/objetos/{id}` es lógico: marca `ELIMINADO`, conserva filas y asociaciones de fotos. La búsqueda y el detalle públicos ocultan el objeto y el perfil del dueño lo conserva en su historial.
- La confirmación cancelada en Mis publicaciones no envía DELETE.

## Pruebas ejecutadas

| Caso | Resultado |
| --- | --- |
| El autor actualiza nombre, descripción, ubicación y fecha | Valores iguales en respuesta PUT, base, detalle y búsqueda |
| PUT con nombre 151, descripción 16.001, ubicación 256 o fecha futura | Cada uno devuelve 400 y deja los datos originales intactos |
| Otro usuario o ADMIN intenta PUT/DELETE | 403, sin cambio |
| Visitante sin token intenta PUT/DELETE | 401, sin cambio |
| Autor confirma DELETE | 204; estado ELIMINADO, fila y foto se conservan, vistas públicas devuelven ausencia y el historial muestra ELIMINADO |
| Repetir DELETE sobre eliminado | 404 |
| Cancelar confirmación en frontend | No se invoca la API; publicación permanece activa |

Comandos:

```bash
cd backend/springboot && mvn -Dtest=EdicionPublicacionIntegrationTest test
cd frontend/usuario && npm test -- --run src/pages/MisPublicaciones.test.jsx
```
