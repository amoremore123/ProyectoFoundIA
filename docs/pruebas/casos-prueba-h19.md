# Casos de prueba — H19 Datos de contacto ocultos para los demás usuarios

Responsable de API y datos: Yajaira (`dev/yajaira`).
Reglas: [`docs/privacidad-datos.md`](../privacidad-datos.md).
Pruebas automáticas (Spring Boot + MockMvc + base H2 temporal):
`controller/PrivacidadIntegrationTest.java` y `dto/UsuarioResumenTest.java`.

Datos de prueba: autora "Ana Pérezvalle" con correo `ana.secreta@prueba.invalid`
y código de verificación `654321`; otro usuario "Luis Gómez".

| ID | Caso | Resultado esperado | Prueba |
| --- | --- | --- | --- |
| CP-H19-01 | Visitante sin sesión en listado, detalle, búsqueda, categoría y coincidencias | Ninguna respuesta contiene el correo, el apellido completo, el hash, el código ni campos `password`, `correo`, `intentos` o `bloqueado` | `visitanteNoVeDatosPrivados` |
| CP-H19-02 | Otro usuario con sesión en las mismas rutas | Igual que CP-H19-01 | `otroUsuarioNoVeDatosPrivados` |
| CP-H19-03 | Forma del autor público | `publicadoPor` = `{ id, nombre: "Ana", apellido: "P." }`, sin correo, rol ni estado | `autorSoloConVistaPublica` |
| CP-H19-04 | Titular en `/api/perfil` | Ve su correo y apellido completos; nunca la contraseña | `titularVeSuPerfil` |
| CP-H19-05 | Otro usuario intenta ver el perfil de la autora | `/api/perfil` solo devuelve el suyo; `/api/perfil/{id}` no existe (404/405) | `perfilNoDevuelveAOtro` |
| CP-H19-06 | Manipular el `id` o provocar errores | 400/404 sin datos privados en el cuerpo del error | `erroresNoExponenDatos` |
| CP-H19-07 | `/api/perfil` sin sesión | 401 sin datos | `perfilSinSesion` |
| CP-H19-08 | Inicial del apellido | "ñandú" → "Ñ.", "Álvarez" → "Á.", vacío o nulo → "" | `inicialDelApellido` |
| CP-H19-09 | Permisos del administrador (Django) | Sin token, `/api/admin/usuarios/` da 401; solo `ADMIN` activo accede | Prueba manual en Docker |

## Resultado de la ejecución — 17 de octubre de 2026

| Suite | Resultado |
| --- | --- |
| `PrivacidadIntegrationTest` | 7 pruebas, 0 fallos |
| `UsuarioResumenTest` | 3 pruebas, 0 fallos |
| Backend completo (`mvn clean test`) | 183 pruebas, 0 fallos, 0 errores |

## Verificación en Docker con la base real — 17 de octubre de 2026

| Caso | Esperado | Obtenido |
| --- | --- | --- |
| Autor en `GET /api/objetos/4` | Solo id, nombre e inicial | `{ id: 1, nombre: "Admin", apellido: "S." }` |
| Correos reales de la base en respuestas públicas (`/objetos`, `/buscar`, `/objetos/1`, `/coincidencias`, `/categoria/1`) | 0 | 0 en todas |
| Django `/api/admin/usuarios/` sin token | 401 | 401 |
| Cuenta USUARIO (QA, eliminada al terminar) inicia sesión en Django | Rechazo | 403 "No autorizado: se requiere rol ADMIN" |
| Token de USUARIO de Spring usado en Django | Rechazo | 401 |
