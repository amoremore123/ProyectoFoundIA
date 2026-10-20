# Casos de prueba — H15 Administrador: gestionar usuarios y publicaciones

Responsable de API y datos: Yajaira (`dev/yajaira`). API en Django: `/api/admin/usuarios/`
y `/api/admin/publicaciones/`. Reglas en [`docs/api/endpoints.md`](../api/endpoints.md#reglas-de-h15-usuarios-y-publicaciones).

## API — `backend/django-admin/nucleo/tests.py` (SQLite en memoria)

Ejecutar dentro del contenedor:

```bash
docker exec foundia-admin-api python manage.py test nucleo --settings=config.settings_test
```

| ID | Caso | Resultado esperado | Prueba |
| --- | --- | --- | --- |
| CP-H15-01 | ADMIN lista usuarios y publicaciones | `200`; sin contraseñas | `test_admin_lista_usuarios_y_publicaciones` |
| CP-H15-02 | Visitante sin token (GET y PATCH) | `401`; nada cambia | `test_visitante_sin_token_no_accede` |
| CP-H15-03 | USUARIO por API directa | `403`; nada cambia | `test_usuario_normal_no_accede_ni_por_api_directa` |
| CP-H15-04 | USUARIO intenta entrar al panel | `403` sin token | `test_usuario_normal_no_inicia_sesion_en_el_panel` |
| CP-H15-05 | ADMIN suspendido | Pierde el acceso (`403`) y no inicia sesión (`401`) | `test_admin_suspendido_pierde_el_acceso` |
| CP-H15-06 | Suspender y activar | Estado guardado en la base | `test_suspender_y_activar_usuario` |
| CP-H15-07 | ADMIN se suspende a sí mismo | `400`; sigue ACTIVO | `test_admin_no_se_suspende_a_si_mismo` |
| CP-H15-08 | Cambiar rol, correo o estado inválido | `400`; nada cambia | `test_no_cambia_rol_correo_ni_estado_invalido` |
| CP-H15-09 | Crear, `PUT` o borrar usuarios | `405`; la cuenta se conserva | `test_no_crea_ni_borra_usuarios_fisicamente` |
| CP-H15-10 | Ocultar, mostrar y eliminar | `OCULTO` → `ACTIVO` → `ELIMINADO`; la fila se conserva | `test_ocultar_mostrar_y_eliminar_logicamente` |
| CP-H15-11 | Mostrar una eliminada | `400`; sigue `ELIMINADO` | `test_eliminada_no_se_vuelve_a_mostrar` |
| CP-H15-12 | `DELETE` físico o editar contenido | `405` / `400`; nada cambia | `test_delete_fisico_y_edicion_de_contenido_bloqueados` |
| CP-H15-13 | `id` inexistente | `404` sin datos | `test_id_inexistente_da_404_sin_datos` |

## Punta a punta en Docker — 20 de octubre de 2026

Cuenta ADMIN de desarrollo (`database/seed.sql`) y una cuenta QA temporal (eliminada al terminar).

| Caso | Resultado |
| --- | --- |
| ADMIN inicia sesión en el panel; USUARIO recibe `403` | OK |
| Token de USUARIO no lista usuarios | OK |
| Listado de usuarios recorre todas las páginas y no muestra contraseñas | OK |
| Ocultar: no se ve en detalle público (`404`) ni en búsqueda | OK |
| Volver a mostrar: detalle público `200` | OK |
| Eliminar lógico: fuera de lo público, la fila se conserva; no se vuelve a mostrar (`400`) | OK |
| `DELETE` físico `405`; editar contenido `400` | OK |
| No se cambia el rol (`400`) | OK |
| Suspender: la sesión abierta del usuario da `401` y no puede iniciar sesión ("suspendida") | OK |
| Reactivar: vuelve a iniciar sesión (`200`) | OK |
| El ADMIN no se suspende a sí mismo (`400`); no se borran usuarios (`405`) | OK |

**Total API: 26/26 casos correctos (junto con H14).**

## Panel de administración (navegador)

| Caso | Resultado |
| --- | --- |
| Publicaciones: se ven los 8 registros (antes el panel cortaba en la primera página de 10) | OK |
| Publicación eliminada: "Sin acciones"; activa: Ocultar y Eliminar | OK |
| Usuarios: la cuenta propia muestra "(tú) · Tu cuenta", sin botón de suspender | OK |
| Suspender con modal de confirmación: estado SUSPENDIDO y botón "Activar" | OK |
| Al perder la autorización (token inválido): se borra la sesión, aviso "Tu sesión venció" y no quedan datos visibles | OK |
| Mensajes de error de Django (`detail`) y de conexión en español | OK (revisado en código; `mensajeError` del panel) |
| Contraste del panel: mismos tokens corregidos que la web de usuario (≥ 4,5:1) | OK |
