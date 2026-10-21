# Casos de prueba — H13 Modificar los datos del perfil propio

Responsable de API y datos: Yajaira (`dev/yajaira`). Endpoint: `PUT /api/perfil`.
Pruebas automáticas: `backend/springboot/src/test/java/com/proyectointegrador/controller/PerfilIntegrationTest.java`
(Spring Boot + MockMvc + base H2 temporal, sin tocar la base del equipo).

## Casos

| ID | Caso | Datos de prueba | Resultado esperado | Prueba |
| --- | --- | --- | --- | --- |
| CP-H13-01 | Datos válidos | nombre `"  Ána María "`, apellido `Núñez` | 200; el mismo valor (sin espacios sobrantes, con tildes) en la respuesta, en la base y en un nuevo `GET /api/perfil` | `actualizaDatosValidos` |
| CP-H13-02 | Sin sesión | PUT sin token | 401; ningún usuario cambia | `sinSesionRechaza` |
| CP-H13-03 | Datos inválidos | nombre vacío, `Ana123`, 101 caracteres, apellido ausente | 400 con el campo indicado; no cambia nada | `datosInvalidos` |
| CP-H13-04 | Elevar rol o modificar a otro | JSON con `id` de otro usuario, `rol: ADMIN`, `correo` y `estado` | 200 solo sobre la cuenta del token; rol sigue `USUARIO`, correo y estado sin cambios; el otro usuario intacto | `noEscalaNiModificaAOtro` |
| CP-H13-05 | Cambio de contraseña sin la actual o con una incorrecta | `password` nueva sin `passwordActual`, o con una equivocada | 400 "La contraseña actual no es correcta…"; no cambia ni el hash ni el nombre | `cambioPasswordExigeLaActual` |
| CP-H13-06 | Contraseña nueva débil o larga | `corta`; más de 72 bytes con `ñ` | 400 con las reglas del registro; sigue valiendo la contraseña anterior | `passwordNuevaDebil` |
| CP-H13-07 | Cambio de contraseña correcto | `Nueva456#` con la actual correcta | 200 sin contraseña ni hash en la respuesta; hash BCrypt en la base; login con la nueva = 200, con la anterior = 401 | `cambioPasswordCorrecto` |
| CP-H13-08 | Guardar sin cambiar contraseña | `password` vacío | 200; el hash no cambia | `sinPasswordNoLaCambia` |

## Resultado de la ejecución — 17 de octubre de 2026

| Suite | Resultado |
| --- | --- |
| `PerfilIntegrationTest` | 8 pruebas, 0 fallos |
| Backend completo (`mvn test`) | 173 pruebas, 0 fallos, 0 errores |

## Pantalla Perfil (frontend/web)

Pruebas automáticas: `frontend/usuario/src/pages/Perfil.test.jsx` (8 pruebas).

| ID | Caso | Resultado esperado |
| --- | --- | --- |
| CP-H13-W1 | Guardar nombre y apellido válidos | Se envían sin contraseña; la pantalla muestra lo que devuelve la API |
| CP-H13-W2 | Cancelar | Restaura los datos cargados, vacía contraseñas y no llama a la API |
| CP-H13-W3 | Datos inválidos | Error junto a cada campo, `aria-invalid`, sin "Reintentar" y sin llamar a la API |
| CP-H13-W4 | Contraseña nueva sin confirmar ni actual | Errores junto a "Confirmar" y "Contraseña actual" |
| CP-H13-W5 | Cambio de contraseña correcto | Envía `password` y `passwordActual`; luego vacía los campos; nada en `localStorage` |
| CP-H13-W6 | La API rechaza la contraseña actual | Error junto a "Contraseña actual"; no hay éxito ficticio; se conserva lo escrito |
| CP-H13-W7 | Fallo del servidor al guardar | Mensaje en español sin "Reintentar" ni éxito |
| CP-H13-W8 | Fallo al cargar el perfil | "Reintentar" recupera el perfil |

## Prueba de punta a punta en navegador — 17 de octubre de 2026

Docker local con la API real, cuenta de QA creada para la prueba y eliminada al terminar.
Vista a 375 px.

| Caso | Esperado | Obtenido |
| --- | --- | --- |
| Registro → código de Mailpit → verificación → login | Sesión iniciada | Correcto |
| Cambiar nombre a "Ána María" y apellido a "Núñez" | "Perfil actualizado correctamente"; al recargar se mantiene | Correcto; en la base `Ána María / Núñez`, rol `USUARIO` |
| Nombre "Ana123" | Error de la API en español | "El campo nombre solo puede contener letras" |
| Contraseña nueva con la actual equivocada | Error junto a "Contraseña actual", sesión abierta | Correcto, sin "Reintentar" |
| Contraseña nueva con la actual correcta | "Perfil y contraseña actualizados", campos vacíos | Correcto; nada en `localStorage` |
| Login posterior | Nueva = 200, anterior = 401 | 200 / 401 |

Suite frontend: 103 pruebas, 0 fallos. Compilación correcta.
