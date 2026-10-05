# Casos de prueba — H1 Registrar objeto, H3 Ubicación y H4 Fecha

**Responsable:** Integrante 2 · **Enfoque:** Publicaciones (crear objeto)

## 1. Pruebas automáticas (backend Spring Boot)

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `dto/ObjetoRequestValidationTest` | Unitaria (Bean Validation) | Campos obligatorios del formulario y fecha no futura (H1, H4) |
| `service/ObjetoServiceTest` | Unitaria (JUnit 5 + Mockito) | Creación de la publicación, ubicación y coordenadas (H1, H3) |
| `controller/ObjetoControllerTest` | Endpoints (MockMvc) | Códigos HTTP y mensajes de `POST /api/objetos` (H1, H4) |

### Cómo ejecutarlas

```powershell
cd backend\springboot
mvn test
```

Resultado esperado: `BUILD SUCCESS`, 0 fallos.

## 2. Casos de prueba — H1 Registrar objeto

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H1-01 | Publicación válida | Mochila negra / descripción / categoría / fecha pasada / tipo | 201, estado ACTIVO, se guarda con su dueño y categoría | `crearValido`, `crearPublicacion` |
| CP-H1-02 | Sin nombre | nombre en blanco | 400, el formulario marca el nombre | `nombreObligatorio`, `crearSinNombre` |
| CP-H1-03 | Sin descripción | descripción vacía | 400, el formulario marca la descripción | `descripcionObligatoria`, `crearSinDescripcion` |
| CP-H1-04 | Sin categoría | no se selecciona categoría | 400, el formulario marca la categoría | `categoriaObligatoria`, `crearSinCategoria` |
| CP-H1-05 | Sin fecha | fecha vacía | 400, el formulario marca la fecha | `fechaObligatoria` |
| CP-H1-06 | Sin tipo | no se elige "Perdí" ni "Encontré" | 400, el tipo es obligatorio | `tipoObligatorio` |
| CP-H1-07 | Categoría inexistente | categoriaId = 99999 | 404 "Categoría no encontrada", no se guarda nada | `categoriaInexistente` |
| CP-H1-08 | Usuario inexistente | token de un usuario borrado | 404, no se guarda nada | `usuarioInexistente` |
| CP-H1-09 | Datos correctos en el formulario | llenar todos los campos | El frontend muestra errores solo de los campos vacíos y envía al backend | Manual |

## 3. Casos de prueba — H3 Ubicación

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H3-01 | Botón "Usar mi ubicación" (permiso concedido) | navegador con GPS | Se llena la dirección y se muestran latitud/longitud; el payload incluye `latitud` y `longitud` | `crearPublicacionConUbicacion` |
| CP-H3-02 | Permiso de ubicación denegado | bloquear el permiso del navegador | Aviso "No se pudo acceder a tu ubicación (permiso denegado)" y se puede escribir a mano | Manual |
| CP-H3-03 | Ubicación escrita a mano | "Biblioteca central" sin botón | Se publica con la dirección y sin coordenadas (null) | Manual |
| CP-H3-04 | Coordenadas guardadas en la BD | publicar con el botón | `SELECT ubicacion, latitud, longitud FROM objetos;` muestra los valores | Manual |

## 4. Casos de prueba — H4 Fecha

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H4-01 | Fecha de hoy | día actual | Se acepta (tanto en el formulario como en la API) | `fechaDeHoy` |
| CP-H4-02 | Fecha pasada | 2026-10-02 | Se acepta, la publicación se crea | `datosValidos`, `crearValido` |
| CP-H4-03 | Fecha futura | mañana | El input no permite elegirla (`max` = hoy) y el formulario marca "La fecha no puede ser futura" | `fechaFutura` |
| CP-H4-04 | Fecha futura enviada a la API | `fechaObjeto` = 3 días adelante | 400 "El campo fechaObjeto no puede ser futura", no se guarda nada | `crearConFechaFutura` |

## 5. Prueba manual de punta a punta

1. Levantar MySQL y el backend (`mvn spring-boot:run` o el jar) y el frontend (`npm run dev`).
2. Iniciar sesión en http://localhost:5173/
3. Abrir **Publicar objeto**, dejar la descripción vacía → botón no publica, marca el campo.
4. Escribir descripción, elegir categoría y ubicación; presionar **📍 Usar mi ubicación** → se rellena la dirección y aparecen las coordenadas.
5. Elegir una fecha de mañana → el calendario no la deja o aparece "La fecha no puede ser futura".
6. Publicar con datos válidos → entra al detalle del objeto recién creado.
7. Ver en la base: `SELECT id, nombre, ubicacion, latitud, longitud, fecha_objeto FROM objetos ORDER BY id DESC LIMIT 1;`
