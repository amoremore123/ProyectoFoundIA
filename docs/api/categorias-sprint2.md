# H26 — categorías administrativas

API Django (`:8000`), JWT de un administrador activo:

- `GET/POST /api/admin/categorias/`: listar o crear (`nombre`, `descripcion`, `estado`).
- `GET/PUT/PATCH /api/admin/categorias/{id}/`: consultar o editar conservando el ID.
- `PATCH {"estado": false}` desactiva; `true` vuelve a activar.
- `DELETE /api/admin/categorias/{id}/` también desactiva: responde 204, **no borra la fila**.

El nombre se recorta y se rechazan vacíos y duplicados sin distinguir mayúsculas,
también entre categorías desactivadas. No se permite asignar IDs o fechas desde
el cliente. Los objetos existentes mantienen su categoría y sus asociaciones.
Spring rechaza nuevas publicaciones o cambios hacia una categoría desactivada
(400). Permite editar un antecedente que ya la tenía, sin cambiar su ID ni borrar
su asociación. El catálogo público conserva las inactivas con `estado: false` para
los filtros históricos de H7. Los formularios de alta deben ofrecer solo las activas;
la API rechaza asignaciones desactivadas aunque el cliente tenga un catálogo antiguo.
Sin sesión: 401. Usuario no ADMIN o suspendido: 403. Datos inválidos: 400.

Pruebas aisladas (SQLite en memoria, nunca la base del equipo):

```powershell
python manage.py test nucleo.test_categorias --settings=config.settings_test --noinput
```
