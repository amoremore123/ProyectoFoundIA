# H20 — Recibir aviso al reclamar una publicación

## Acción mínima definida

`POST /api/objetos/{id}/reclamos` con JWT reclama una publicación activa de tipo
`ENCONTRADO`. El registro guarda objeto, cuenta reclamante, fecha y estado
`PENDIENTE`. No marca automáticamente el objeto como recuperado ni sustituye el
contacto de H18. Se crea un aviso in-app al propietario; otra cuenta no ve ni
modifica ese aviso. Cada cuenta puede reclamar el objeto una sola vez: repetir
la solicitud devuelve el reclamo existente (200) sin segundo aviso.

Para bases ya creadas, aplicar `database/migracion_h20_reclamos.sql`; las nuevas
bases obtienen la tabla desde `database/script.sql`.

## Pruebas ejecutadas

| Caso | Resultado |
| --- | --- |
| Reclamo válido de otro usuario | 201; persistidos objeto, reclamante, estado y fecha; notificación `RECLAMO_RECIBIDO` al dueño correcto |
| Cuenta no destinataria consulta o marca el aviso | No obtiene ni modifica la notificación ajena |
| Propietario reclama su propio objeto, objeto perdido o ID inexistente | 403/400/404, sin reclamo ni notificación |
| Sin sesión | 401, sin efecto |
| Repetición | 200 con `repetido=true`; una sola fila y un solo aviso |
| Frontend | Solo ofrece reclamar hallazgos ajenos a una sesión; muestra el resultado |

```bash
cd backend/springboot && mvn -Dtest=ReclamoIntegrationTest test
```
