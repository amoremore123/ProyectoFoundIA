# Casos de prueba — H14 Visualizar las publicaciones propias

Responsable de API y datos: Yajaira (`dev/yajaira`). Endpoint: `GET /api/perfil/objetos`
(identidad tomada del token; no existe un listado ajeno por `id`).

## API — `PublicacionesPropiasIntegrationTest` (Spring + H2 temporal)

| ID | Caso | Resultado esperado | Prueba |
| --- | --- | --- | --- |
| CP-H14-01 | Cuenta con publicaciones | Solo las 2 propias, con foto, estado (`ACTIVO`, `OCULTO`) y categoría correctos | `soloLasPropiasConFotoYEstado` |
| CP-H14-02 | Cuenta sin publicaciones | `200` con `[]` (no es un error) | `cuentaSinPublicaciones` |
| CP-H14-03 | Otra sesión | Luis solo ve la suya; nunca las de Ana | `noMezclaOtraSesion` |
| CP-H14-04 | Después de editar | El listado muestra el nombre y la ubicación nuevos | `reflejaLaEdicion` |
| CP-H14-05 | Después de eliminar | Sigue en el historial propio como `ELIMINADO`; detalle público = 404 | `reflejaLaEliminacion` |
| CP-H14-06 | Sin token | `401` sin datos | `sinToken` |
| CP-H14-07 | Listado ajeno cambiando el `id` | `/api/usuarios/{id}/objetos` y `/api/perfil/objetos/{id}` no existen; sin datos ajenos | `sinListadoAjenoPorId` |
| CP-H14-08 | Publicación OCULTA de otra cuenta | No aparece en lo público ni en las publicaciones de otro usuario | `ocultaAjenaNoSeFiltra` |

## Pantallas — `MisPublicaciones.test.jsx` y `EditarPublicacion.test.jsx`

| ID | Caso | Resultado esperado |
| --- | --- | --- |
| CP-H14-W1 | Lista vacía | "Aún no has publicado nada." y acceso a publicar |
| CP-H14-W2 | Estados y acciones | `ACTIVO`: Editar y Eliminar · `OCULTO`: solo Editar y aviso · `ELIMINADO`: sin acciones; foto visible |
| CP-H14-W3 | Editar | Abre `/mis-publicaciones/{id}/editar` con los datos cargados |
| CP-H14-W4 | Cancelar el modal / Escape | No elimina; el foco empieza en "Cancelar" |
| CP-H14-W5 | Confirmar el modal | Eliminación lógica; se conserva como Eliminado al recargar |
| CP-H14-W6 | Rechazo del servidor al eliminar (403) | Mensaje claro, sigue Activo, sin éxito ficticio |
| CP-H14-W7 | Fallo al cargar | Error distinto de la lista vacía; Reintentar recupera |
| CP-H14-W8 | Guardar edición | `PUT` con los datos y vuelve a Mis publicaciones |
| CP-H14-W9 | Validaciones al editar | Nombre vacío no se envía (mismas reglas que Publicar) |
| CP-H14-W10 | Edición rechazada (403) | "No puedes editar esta publicación…" sin éxito |
| CP-H14-W11 | Abrir una ajena o eliminada | "No encontramos esta publicación entre las tuyas…" sin formulario |
| CP-H14-W12 | Cancelar la edición | Vuelve sin guardar |

## Resultado — 20 de octubre de 2026

| Suite | Resultado |
| --- | --- |
| `PublicacionesPropiasIntegrationTest` | 8 pruebas, 0 fallos |
| Frontend usuario (`vitest run`) | 124 pruebas, 0 fallos |
| Backend completo (`mvn clean test`) | 204 pruebas, 0 fallos |

Punta a punta en Docker (cuenta QA eliminada al terminar): lista vacía `[]`; dos
publicaciones propias `ACTIVO`; sin token `401`; tras ocultarla el ADMIN se ve
`OCULTO` y la autora puede corregirla; tras eliminarla se ve `ELIMINADO`. En el
navegador a 375 px: Eliminado sin acciones, Activo con Editar/Eliminar, modal con
foco en Cancelar y Escape sin eliminar.
