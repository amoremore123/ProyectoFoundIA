# H42 — Reclamar un objeto en custodia con pregunta de verificación

Responsable de la API: Amore. Web: Yajaira (`Detalle.jsx`, `EstadoReclamoCustodia.jsx`).
Android: Amore (`ReclamoCustodiaScreen`). La bandeja de la biblioteca es H43.

Todas las rutas requieren sesión (JWT) y usan la cuenta del token, nunca un id enviado.

## `GET /api/objetos/{id}/reclamos/custodia`

Estado del reclamo propio sobre ese objeto.

```json
{ "id": 7, "objetoId": 3, "estado": "PENDIENTE", "fechaReclamo": "2026-11-10T15:20:00", "repetido": false }
```

`estado` puede ser el del reclamo propio (`PENDIENTE`, `ACEPTADO`, `RECHAZADO`, `RETIRADO`)
o, si no hay reclamo propio:

- `SIN_RECLAMO`: el objeto se puede reclamar (`id` y `fechaReclamo` en `null`).
- `ASIGNADO_A_OTRO`: otra persona ya tiene un reclamo aceptado; no se ofrece el formulario.

## `POST /api/objetos/{id}/reclamos/custodia`

```json
{ "respuesta": "Tiene un llavero azul con mis iniciales" }
```

- `respuesta`: obligatoria, de 3 a 500 caracteres tras quitar espacios. Solo la ve el
  personal en la bandeja protegida de H43; nunca se devuelve al público.
- El objeto se bloquea por fila (`findByIdForUpdate`) mientras se registra.

| Código | Caso |
| --- | --- |
| `201` | Reclamo creado (`repetido: false`) |
| `200` | Ya existía un reclamo propio: se devuelve el mismo (`repetido: true`), no se duplica |
| `400` | Respuesta vacía, de menos de 3 o de más de 500 caracteres |
| `400` | El objeto no es `ENCONTRADO` + `EN_CUSTODIA` |
| `400` | El objeto ya fue asignado a otra persona |
| `401` | Sin token |
| `403` | Es la publicación propia |
| `404` | El objeto no existe |

`POST /api/objetos/{id}/reclamos` (sin `/custodia`) sigue siendo el reclamo simple de
objetos `ACTIVO`; para un objeto en custodia responde `400` y exige la respuesta de verificación.

## Base de datos

`database/migracion_h42_respuesta_custodia.sql` agrega `respuesta_verificacion` a `reclamos`
(idempotente; también incluida en `script.sql`).

## Verificación

Ver [entrega de Amore](../integracion/entrega-amore-sprint3.md) y
[custodia-avisos-invitacion.json](../pruebas/sprint3-amore/custodia-avisos-invitacion.json).
