# Privacidad de los datos de usuario (H19)

Decisión del equipo (17/10/2026): **el correo y los demás datos de contacto
son siempre privados**. Otra persona nunca recibe el correo de un usuario; el
contacto entre usuarios se hará siempre a través de la plataforma (H18).

## Qué es público y qué es privado

| Dato | Otro usuario o visitante | Titular (`/api/perfil`) | ADMIN (API Django) |
| --- | --- | --- | --- |
| `id` | Sí | Sí | Sí |
| `nombre` | Sí | Sí | Sí |
| `apellido` | **Solo la inicial** ("P.") | Completo | Completo |
| `correo` (dato de contacto) | **Nunca** | Sí | Sí, para gestionar cuentas |
| `rol`, `estado` | Nunca | Sí | Sí |
| `password` / hash | Nunca | Nunca | Nunca |
| Código de verificación, intentos, bloqueo | Nunca | Nunca | Nunca |

Si en el futuro se agrega un teléfono u otro dato de contacto, entra en la fila
de `correo`: privado por defecto.

## Dónde se aplican las reglas

Las reglas viven en la **API**, no solo en la pantalla: un dato que no debe verse
no se envía.

| Lugar | Regla |
| --- | --- |
| `UsuarioResumen.publico(usuario)` (Spring) | Única forma de armar al autor que ven otros: `id`, `nombre` e inicial del apellido. |
| `ObjetoResponse.publicadoPor` | Listado, detalle, búsqueda, filtro por categoría, publicaciones propias. Usa `UsuarioResumen.publico`. |
| Coincidencias (`/api/objetos/{id}/coincidencias`) | No incluyen datos del autor. H16/H17 deben usar `UsuarioResumen.publico` si los agregan. |
| Contacto (H18) | Debe vincular a las cuentas sin revelar el correo; mostrar solo `UsuarioResumen.publico`. |
| `/api/perfil` | Siempre devuelve el usuario del token. No hay ruta para pedir el perfil de otra persona por `id`. |
| API Django `/api/admin/...` | Solo rol `ADMIN` y estado `ACTIVO` (`EsAdmin`); sin token responde 401. |
| Errores (404, 400) | Mensajes genéricos; nunca incluyen datos del usuario. |

## Pruebas

- `PrivacidadIntegrationTest` (Spring): visitante y otro usuario no reciben
  correo, apellido completo, hash, código ni intentos en ninguna ruta pública;
  el titular sí ve su perfil; manipular el `id` o provocar errores no expone datos.
- `UsuarioResumenTest`: la vista pública solo tiene `id`, `nombre` e inicial.
- Casos documentados en [`docs/pruebas/casos-prueba-h19.md`](pruebas/casos-prueba-h19.md).
