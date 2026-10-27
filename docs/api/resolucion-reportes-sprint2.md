# H28 — resolución administrativa auditada

Requiere JWT de administrador activo en Django (`:8000`).

- `GET /api/admin/reportes/` y `GET /api/admin/reportes/{id}/`: consultar.
- `POST /api/admin/reportes/{id}/resolver/`: resolver/rechazar.
- `PATCH /api/admin/reportes/{id}/` admite el mismo cuerpo, para compatibilidad.

```json
{"estado": "RESUELTO", "resolucion": "Publicación revisada por administración"}
```

Solo se aceptan `RESUELTO` o `RECHAZADO` y una explicación no vacía de hasta
1000 caracteres. Solo los reportes pendientes o revisados pueden resolverse.
El actor y la fecha UTC salen de la sesión y del servidor; campos adicionales
son 400. Se conserva la publicación, el denunciante, el motivo y la descripción.

Respuesta: `id`, `estado`, `resolucion`, `resueltoPorId`, `fechaResolucion`,
`repetido`. Repetir exactamente la misma resolución devuelve 200 y `repetido: true`,
conservando la fecha y el actor originales. Cambiarla después devuelve 409.
Sin sesión: 401; no ADMIN/suspendido: 403; inexistente: 404.
No hay alta, borrado físico ni edición general de reportes desde el panel (405).

La migración `database/migracion_h28_auditoria_reportes.sql` se verificó y
reejecutó en la base compartida el 2026-10-26 con respaldo previo; la base ya
contenía las columnas y los datos existentes quedaron preservados. Para otra
base, revisar y autorizar antes de aplicarla. **No reimportar script.sql ni
seed.sql**.

Pruebas: `python manage.py test nucleo.test_resolucion --settings=config.settings_test --noinput`.
