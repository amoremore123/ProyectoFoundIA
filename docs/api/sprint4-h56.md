# H56 — Resolver los reportes de mensajes del chat en el panel

Responsable del backend: Leonel (Django). Web: Yajaira (panel de administración).
Completa H56: el bloqueo y el reporte desde el chat ya están en Spring ([contrato del chat](sprint4-h55.md));
hasta ahora los reportes solo se guardaban en `reportes_mensaje` y nadie podía verlos.

Reutiliza las reglas de H28 (resolución auditada de reportes de publicaciones) y de H15 (suspender cuentas).

## Rutas (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/reportes-mensajes/?estado=PENDIENTE\|RESUELTO\|RECHAZADO` | Bandeja paginada, el más antiguo primero |
| `GET` | `/api/admin/reportes-mensajes/{id}/` | Detalle |
| `POST` | `/api/admin/reportes-mensajes/{id}/resolver/` | Resolver |

No hay alta, edición ni borrado (`405`): los reportes los crea el chat y quedan como historial.

### Cada reporte

```json
{
  "id": 4,
  "estado": "PENDIENTE",
  "motivo": "Me escribió insultos",
  "fechaReporte": "2026-11-22T15:20:00Z",
  "conversacionId": 7,
  "mensaje": { "id": 31, "texto": "…", "imagenUrl": null, "enviadoEn": "2026-11-22T15:18:00Z" },
  "autor": { "id": 3, "nombre": "Luis García", "estado": "ACTIVO" },
  "reportadoPor": { "id": 2, "nombre": "Ana Pérez" },
  "accion": null, "resolucion": null, "resueltoPor": null, "fechaResolucion": null
}
```

El administrador ve el texto y el autor del mensaje para poder decidir. **No se muestran** correos ni
teléfonos.

### `POST …/resolver/`

```json
{ "accion": "ADVERTIR", "resolucion": "Lenguaje ofensivo en el chat" }
```

| `accion` | Estado del reporte | Qué hace además |
| --- | --- | --- |
| `DESCARTAR` | `RECHAZADO` | Nada: el reporte no procede |
| `ADVERTIR` | `RESUELTO` | Envía al autor un aviso `SISTEMA` con la advertencia y la `resolucion`. **No dice quién reportó** |
| `SUSPENDER` | `RESUELTO` | Suspende la cuenta del autor (`SUSPENDIDO`, como H15): su sesión deja de valer en Spring y Django |

- `resolucion`: obligatoria, de 3 a 1000 caracteres. Solo se aceptan `accion` y `resolucion`
  (`400` con otros campos): el responsable y la fecha los registra el servidor.
- Solo se resuelven reportes `PENDIENTE`. Repetir **exactamente** la misma resolución devuelve `200`
  con `repetido: true` sin repetir el aviso ni la suspensión; cambiarla después devuelve `409` (como H28).
- No se puede suspender la propia cuenta (`400`, como H15).
- Si el autor ya estaba suspendido, `SUSPENDER` resuelve el reporte sin cambiar nada más.

| Código | Caso |
| --- | --- |
| `200` | Resuelto (o repetido) |
| `400` | Acción o resolución inválida, campos extra, suspender la propia cuenta, filtro de estado inválido |
| `401` / `403` | Sin sesión / rol distinto de `ADMIN` (incluida `BIBLIOTECA`) |
| `404` | El reporte no existe |
| `409` | Ya tenía otra resolución |

## Base de datos

`database/migracion_h56_resolver_reportes_mensaje.sql` (idempotente, con respaldo previo; también en
`script.sql`) agrega a `reportes_mensaje`: `accion`, `resolucion`, `resuelto_por_id` y `fecha_resolucion`.
Las filas existentes quedan `PENDIENTE` y sin resolución.

## Pruebas que se deben ejecutar y registrar

- `ADMIN` ve la bandeja con el texto y el autor; `BIBLIOTECA` y `USUARIO` reciben `403`; sin sesión `401`.
- `DESCARTAR` → `RECHAZADO`, sin avisos ni suspensión.
- `ADVERTIR` → `RESUELTO` y un aviso `SISTEMA` para el autor que no menciona a quien reportó.
- `SUSPENDER` → `RESUELTO`, la cuenta queda `SUSPENDIDO` y su sesión de Spring deja de valer (`401`).
- Repetir la misma resolución `200` y `repetido: true` (sin otro aviso); otra distinta `409`.
- Suspender la propia cuenta `400`; campos extra `400`; filtro `?estado=` correcto.
