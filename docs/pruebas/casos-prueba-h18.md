# H18 — Contactar a quien encontró un objeto similar

## Contrato mínimo

El endpoint autenticado `POST /api/contactos` recibe `objetoPerdidoId` y
`objetoEncontradoId`. El solicitante debe ser el autor de la publicación perdida;
ambas publicaciones deben estar activas, ser de tipos opuestos y compartir
categoría. La API crea o reutiliza la coincidencia persistida y añade un contacto
PENDIENTE. Genera una notificación in-app únicamente al autor de la publicación
encontrada. No expone datos de contacto ni abre un chat. Iniciar contacto no
equivale a reclamar.

Regla de repetición: una solicitud repetida por el mismo autor y la misma pareja
es idempotente; devuelve el registro existente y no duplica ni contacto ni aviso.

## Pruebas ejecutadas

| Caso | Resultado |
| --- | --- |
| Pareja válida y autor de la publicación perdida | 201; persistidos `Coincidencia` y `Contacto`, notificado el dueño del hallazgo |
| Respuesta y aviso | No contienen correo ni apellido completo; solo destinatario recibe `CONTACTO_RECIBIDO` |
| Repetición de la misma solicitud | 200 con `repetido=true`; una fila de contacto y una notificación |
| Sin sesión, autor incorrecto, tipo/categoría incompatible o ID inexistente | 401/403/400/404; sin contacto ni notificación |
| Marcar aviso leído | Solo el destinatario ve y cambia su propia notificación; queda persistida |
| Frontend en coincidencia | Solo el dueño de un objeto perdido ve Contactar; confirma envío/repetición y permite reintentar un fallo de red |

```bash
cd backend/springboot && mvn -Dtest=ContactoIntegrationTest test
```

```bash
cd frontend/usuario && npm test -- --run src/pages/Detalle.test.jsx
```
