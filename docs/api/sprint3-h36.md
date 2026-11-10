# H36 — Rol BIBLIOTECA y permisos

> Estado de esta entrega: API/datos implementados y probados en Spring/Django.
> Las pantallas web (Yajaira) y Android (Amore) quedan pendientes en sus capas.

## Roles

| Rol | Spring | Django | Puede |
| --- | --- | --- | --- |
| `USUARIO` | `ROLE_USER` | sin panel | publicar, reclamar, ver lo propio |
| `BIBLIOTECA` | `ROLE_BIBLIOTECA` | `EsBibliotecaOAdmin` | custodia y reclamos, no sistema |
| `ADMIN` | `ROLE_ADMIN` | `EsAdmin` | sistema completo y asignar roles |

Solo un `ADMIN` activo cambia roles. El cambio exige `confirmar=true`, deja
auditoría en `cambios_rol` y nunca permite quitar el rol al propio administrador
ni al último `ADMIN` del sistema.

## `PATCH /api/admin/usuarios/{id}/` (Django)

Cabecera: `Authorization: Bearer <token ADMIN>`.

```json
{
  "rol": "BIBLIOTECA",
  "confirmar": true,
  "motivo": "Apoya la atención de custodia"
}
```

Respuesta `200`:

```json
{
  "id": 12,
  "nombre": "Ana",
  "apellido": "Pérez",
  "correo": "ana@foundia.dev",
  "rol": "BIBLIOTECA",
  "estado": "ACTIVO",
  "cambioRol": {
    "id": 3,
    "rolAnterior": "USUARIO",
    "rolNuevo": "BIBLIOTECA",
    "cambiadoPor": "admin@foundia.dev",
    "fecha": "2026-11-09T15:00:00Z"
  }
}
```

Errores: `400` sin confirmación, rol inválido, cambio del propio rol o último
ADMIN; `403` si quien lo pide no es ADMIN; `401` sin sesión.

## `GET /api/admin/usuarios/{id}/cambios-rol/` (Django)

Devuelve hasta 50 cambios del usuario, del más reciente al más antiguo, con
`rolAnterior`, `rolNuevo`, `cambiadoPor`, `motivo` y `fecha`. Solo ADMIN.

## `PATCH /api/admin/usuarios/{id}/` con estado

Se conserva H15: `{"estado": "ACTIVO" | "SUSPENDIDO"}`. No se mezcla con `rol`.

## Login del panel

`POST /api/admin/auth/login/` acepta `ADMIN` y `BIBLIOTECA`. Un `BIBLIOTECA`
recibe token, pero las rutas de sistema (`usuarios`, `dashboard`, `categorias`,
`reportes`) responden `403`. Las rutas de custodia y reclamos de H41/H43 usan
`EsBibliotecaOAdmin`.

## Datos

- `usuarios.rol` admite `USUARIO`, `BIBLIOTECA`, `ADMIN`.
- `cambios_rol` guarda `usuario_id`, `rol_anterior`, `rol_nuevo`,
  `cambiado_por_id`, `motivo` y `fecha_cambio`.
- Migración: `database/migracion_h36_rol_biblioteca.sql`.
