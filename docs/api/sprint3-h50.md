# H50 — Recibir en el celular el aviso de mi reclamo

Responsable de la API: Amore. Android: Amore (`AvisosReclamoWorker` con WorkManager).

## `GET /api/notificaciones/reclamos/novedades?desde=<fecha>&despuesDe=<id>`

Requiere sesión. Solo devuelve avisos de la cuenta del token.

| Parámetro | Obligatorio | Descripción |
| --- | --- | --- |
| `desde` | Sí | Fecha ISO local (`2026-11-10T00:00:00`), interpretada como UTC |
| `despuesDe` | No (por defecto `0`) | Cursor: id del último aviso ya recibido |

Respuesta: lista de avisos (`NotificacionResponse`) de tipo `RECLAMO`, sin leer, creados desde
`desde` y con id mayor que `despuesDe`, en orden de id ascendente. Máximo 50 por consulta.

```json
[ { "id": 31, "titulo": "Tu reclamo fue aceptado", "mensaje": "...", "tipo": "RECLAMO",
    "leida": false, "fechaCreacion": "2026-11-10T15:20:00", "enlace": "/objeto/3" } ]
```

- Si la preferencia `reclamos` de H24 está desactivada, devuelve `[]`.
- Consultar no marca los avisos como leídos.

| Código | Caso |
| --- | --- |
| `200` | Lista (puede ser vacía) |
| `400` | `desde` ausente («Falta el parámetro desde») o con formato inválido; `despuesDe` negativo |
| `401` | Sin token |

## Android

WorkManager consulta la ruta con el permiso de notificaciones y la preferencia activos,
guarda el cursor por cuenta y la notificación del sistema abre la bandeja autenticada.
WorkManager puede retrasar la consulta según la batería y el estado del dispositivo.

## Verificación

- `NovedadesReclamosIntegrationTest` (**4 pruebas**): sin sesión `401`; `desde` ausente o inválido y
  cursor negativo `400`; solo avisos `RECLAMO` propios, en orden y con cursor, sin marcarlos como
  leídos; con la preferencia `reclamos` apagada devuelve `[]`.
- Antes de esta prueba, `desde` ausente respondía `500`. Se corrigió en `GlobalExceptionHandler`
  (`MissingServletRequestParameterException` → `400`), y la suite de Spring quedó en 502 pruebas sin fallos (19/11/2026).
- Ver [entrega de Amore](../integracion/entrega-amore-sprint3.md) y
[custodia-avisos-invitacion.json](../pruebas/sprint3-amore/custodia-avisos-invitacion.json).
La prueba con la app cerrada en un teléfono físico queda en M2 del Sprint 4.
