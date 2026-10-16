# Casos de prueba — H1 Registrar objeto, H3 Ubicación y H4 Fecha

**Responsable:** Integrante 2 · **Enfoque:** Publicaciones (crear objeto)

## 1. Pruebas automáticas (backend Spring Boot)

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `dto/ObjetoRequestValidationTest` | Unitaria (Bean Validation) | Campos obligatorios, límites de nombre/descripcion/ubicación, UTF-8 y fecha no futura (H1, H3, H4) |
| `service/ObjetoServiceTest` | Unitaria (JUnit 5 + Mockito) | Creación de la publicación, ubicación y coordenadas (H1, H3) |
| `controller/ObjetoControllerTest` | Endpoints (MockMvc) | Códigos HTTP y mensajes de `POST /api/objetos` (H1, H3, H4) |

### Cómo ejecutarlas

```powershell
cd backend\springboot
mvn test
```

Resultado esperado: `BUILD SUCCESS`, 0 fallos.

Las pruebas React están en `frontend/usuario/src/pages/Publicar.test.jsx`.
Desde `frontend/usuario`, ejecutar `npm ci`, `npm test` y `npm run build`.
Simulan la API para comprobar validaciones, payload y navegación al detalle.

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
| CP-H1-08 | Usuario inexistente (servicio) | usuarioId inexistente al crear la publicación | El servicio devuelve 404 y no guarda nada; esta prueba no simula el filtro JWT | `usuarioInexistente` |
| CP-H1-09 | Datos correctos en el formulario | llenar todos los campos | El frontend muestra errores solo de los campos vacíos y envía al backend | Manual |
| CP-H1-10 | Límite del nombre | 150 y 151 caracteres | 150 se acepta; 151 da 400 antes de guardar; React limita y valida el nombre | `limiteNombre`, `limitesNombreEnApi`, `Publicar.test.jsx` |
| CP-H1-11 | Capacidad de descripción | 15.999/16.000/16.001 y 70.000 caracteres; ASCII, `é` y emoji | El máximo API/UI es 16.000 unidades UTF-16 (máximo 48.000 bytes para BMP de 3 bytes); los excesos responden 400 con error `descripcion`. Cabe en `TEXT` (65.535 bytes) | `limiteDescripcionCompatibleConTextUtf8mb4`, `limitesDescripcionEnApi`, `Publicar.test.jsx` |
| CP-H1-12 | Importación y sesión UTF-8 | Seed (`Cédula`, `cómputo`, `ñ`) y publicación de prueba con emoji | Sesión cliente/conexión/resultados `utf8mb4`; `HEX('é') = C3A9`; texto leído coincide con lo enviado, no hay doble codificación | MySQL QA aislada, MySQL 8.0.46 |

## 3. Casos de prueba — H3 Ubicación

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H3-01 | Botón "Usar mi ubicación" (permiso concedido) | navegador con GPS | Se llena la dirección y se muestran latitud/longitud; el payload incluye `latitud` y `longitud` | `crearPublicacionConUbicacion` |
| CP-H3-02 | Permiso de ubicación denegado | bloquear el permiso del navegador | Aviso "No se pudo acceder a tu ubicación (permiso denegado)" y se puede escribir a mano | `Publicar.test.jsx` y manual |
| CP-H3-03 | Ubicación escrita a mano | "Biblioteca central" sin botón | Se publica con la dirección y sin coordenadas (null) | `ubicacionManualSinCoordenadas`, `Publicar.test.jsx` |
| CP-H3-04 | Coordenadas guardadas en la BD | publicar con el botón | `SELECT ubicacion, latitud, longitud FROM objetos;` muestra los valores | Manual |
| CP-H3-05 | Dirección obligatoria | ubicación ausente, null, vacía o con espacios | 400 antes de guardar; no basta con omitir la dirección | `ubicacionObligatoria`, `crearSinUbicacion`, `Publicar.test.jsx` |
| CP-H3-06 | Límite de la dirección | 255 y 256 caracteres | 255 se acepta; 256 da 400; React aplica el mismo límite | `limiteUbicacion`, `crearConUbicacionLarga`, `Publicar.test.jsx` |
| CP-H3-07 | Coordenadas opcionales | dirección manual y coordenadas null | Validación correcta y publicación permitida | `ubicacionManualSinCoordenadas`, `Publicar.test.jsx` |
| CP-H3-08 | Rangos geográficos | latitud entre -90 y 90, longitud entre -180 y 180; probar límites y valores fuera de rango | Los límites se aceptan; fuera del rango devuelve 400, no 500; los valores null siguen permitidos | `coordenadasPermitidas`, `coordenadasFueraDeRango`, `crearConCoordenadasFueraDeRango` |
| CP-H3-09 | Dirección editada después del GPS | obtener GPS y cambiar el texto de ubicación | Desaparece el indicador GPS y se publica la dirección manual con coordenadas null | `Publicar.test.jsx`: borra coordenadas al editar la dirección |
| CP-H3-10 | GPS pendiente y edición manual | iniciar GPS, escribir otra dirección y recibir una respuesta tardía | La respuesta anterior no cambia la dirección ni restaura coordenadas; otra consulta GPS puede iniciarse sin sobrescrituras | `Publicar.test.jsx`: resultado tardío y nueva consulta |
| CP-H3-11 | Geocodificación no disponible | GPS correcto pero sin dirección devuelta | Se usa el texto de las coordenadas, nunca una dirección manual anterior; se espera al GPS antes de publicar | `Publicar.test.jsx`: sin geocodificación y espera antes de publicar |

## 4. Casos de prueba — H4 Fecha

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H4-01 | Fecha de hoy | día actual | Se acepta (tanto en el formulario como en la API) | `fechaDeHoy` |
| CP-H4-02 | Fecha pasada | 2026-10-02 | Se acepta, la publicación se crea | `datosValidos`, `crearValido` |
| CP-H4-03 | Fecha futura | mañana | El input no permite elegirla (`max` = hoy) y el formulario marca "La fecha no puede ser futura" | `fechaFutura`, `Publicar.test.jsx` |
| CP-H4-04 | Fecha futura enviada a la API | `fechaObjeto` = 3 días adelante | 400 "El campo fechaObjeto no puede ser futura", no se guarda nada | `crearConFechaFutura` |
| CP-H4-05 | Fecha requerida por API | sin `fechaObjeto` | 400 con error de `fechaObjeto`, no se invoca el servicio | `crearSinFecha` |
| CP-H4-06 | Formato inválido | `16/10/2026`, `2026-02-30` | 400 con mensaje que identifica `fechaObjeto` y especifica `yyyy-MM-dd`; no se invoca el servicio | `crearConFormatoFechaInvalido` |
| CP-H4-07 | Fecha guardada | fecha ISO enviada en la publicación | POST, columna `fecha_objeto` y GET de detalle devuelven el mismo día | `ObjetoPublicacionIntegrationTest` |

## 5. Prueba manual de punta a punta

1. Levantar MySQL y el backend (`mvn spring-boot:run` o el jar) y el frontend (`npm run dev`).
2. Iniciar sesión en http://localhost:5173/
3. Abrir **Publicar objeto**, dejar la descripción vacía → botón no publica, marca el campo.
4. Escribir descripción, elegir categoría y ubicación; presionar **📍 Usar mi ubicación** → se rellena la dirección y aparecen las coordenadas.
5. Elegir una fecha de mañana → el calendario no la deja o aparece "La fecha no puede ser futura".
6. Publicar con datos válidos → entra al detalle del objeto recién creado.
7. Ver en la base: `SELECT id, nombre, ubicacion, latitud, longitud, fecha_objeto FROM objetos ORDER BY id DESC LIMIT 1;`
8. Repetir el GPS, editar la dirección a mano y publicar: el texto nuevo se conserva y `latitud`/`longitud` quedan null.
9. Repetir con permiso de ubicación denegado: aparece el aviso y se puede publicar con una dirección manual.
10. Iniciar el GPS y escribir una dirección mientras llega la respuesta: el resultado tardío no debe reemplazarla. Sin geocodificación, el formulario debe mostrar el texto de las coordenadas en vez de asociarlas a una dirección anterior.

## 6. Verificación de regresión — 6 de octubre de 2026

Código comprobado: `6a3c8dd` (incluye las ocho correcciones coordinadas del sprint).
Resultado general y comandos: [verificación H11/H12](casos-prueba-h11-h12.md#5-verificación-de-regresión--6-de-octubre-de-2026).

| Comprobación adicional | Resultado |
| --- | --- |
| API real + MySQL 8.0.46 | Nombre de 151 caracteres, ubicación vacía/larga y coordenadas fuera de rango devuelven 400; los límites permitidos y la publicación manual sin coordenadas devuelven 201 |
| Navegador + API real + consulta SQL | Cinco publicaciones: GPS editado, GPS sin editar, respuesta tardía, permiso denegado y GPS sin geocodificación; payload y valores guardados coinciden |
| H4 sin cambios funcionales | Fechas de hoy/pasadas aceptadas; futura rechazada por API y formulario |

El GPS y la geocodificación se simularon para repetir los escenarios; no es una
prueba de sensores físicos ni del servicio externo. Todas las escrituras se
hicieron en bases descartables, sin modificar datos del equipo.

## 7. H1 — UTF-8, capacidad `TEXT` y límites de publicación — 16 de octubre de 2026

### Configuración registrada

- `database/script.sql` y `database/seed.sql` inician la sesión importadora con
  `SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci`; la base se crea con ese charset.
- El cliente CLI recomendado usa `--default-character-set=utf8mb4` y Django
  establece `OPTIONS.charset=utf8mb4` al conectar.
- `objetos.descripcion` es `TEXT`, con capacidad máxima de 65.535 bytes. El
  límite acordado de interfaz/API es 16.000 unidades UTF-16. El peor caso BMP
  usado en la prueba (`U+0800`, tres bytes UTF-8) ocupa 48.000 bytes. El máximo
  queda por debajo de la capacidad incluso contando el overhead de fila.

### Ejecución en base QA vacía

Se levantó MySQL 8.0.46 en un contenedor aislado recién inicializado; se
importaron `script.sql` y `seed.sql` y se consultó la base antes de insertar el
caso de publicación. La sesión reportó `character_set_client`,
`character_set_connection` y `character_set_results` como `utf8mb4`, y la
collation de sesión como `utf8mb4_0900_ai_ci` (del servidor). En seed:
`Cédula de identidad` se conservó y `HEX(nombre)` comenzó `43C3A9`;
`HEX('é')` devolvió `C3A9`. Se insertó y volvió a leer `Cédula, cómputo, ñ y
emojis 😃`, con su emoji en UTF-8 `F09F9883`; la columna reportó `TEXT` y
65.535 bytes. No hubo mojibake (`C383C2A9`).

### Resultados automáticos H1

| Prueba | Resultado |
| --- | --- |
| `mvn clean -Dtest=ObjetoControllerTest,ObjetoRequestValidationTest test` | 46 pruebas: 0 fallos, 0 errores |
| `npm test -- --run src/pages/Publicar.test.jsx` | 19 pruebas: todas pasaron |
| Descripción ASCII/acento/emoji abajo y en límite, nombre 150, y error de campo en 16.001/70.000 | Aceptadas/rechazadas según el contrato; no se invocó el servicio para los rechazos |

## 8. H3 — ubicación y coordenadas — 16 de octubre de 2026

Se amplió `POST /api/objetos` para verificar la ubicación de 255 caracteres
(incluidas `ó` y `ñ`), el rechazo de 256, ausencia de ubicación, coordenadas
en los extremos ±90/±180, valores fuera de rango y las dos coordenadas vacías
para dirección manual. El servicio comprueba que copia la dirección, tildes y
coordenadas sin normalizarlas.

La prueba de integración `ObjetoPublicacionIntegrationTest` hace una publicación
por el endpoint autenticado, valida los campos en la respuesta, compara valores
persistidos en la tabla y vuelve a leer el detalle `GET /api/objetos/{id}`.
La persistencia se ejecutó contra H2 efímera; la prueba de sesión y bytes
UTF-8 en MySQL 8.0.46 está registrada en la sección anterior.

| Ejecución | Resultado |
| --- | --- |
| `mvn clean -Dtest=ObjetoControllerTest,ObjetoRequestValidationTest,ObjetoServiceTest,ObjetoPublicacionIntegrationTest test` | 58 pruebas: 0 fallos, 0 errores |
| API → persistencia → detalle | Ubicación `Laboratorio de cómputo, edificio ñ` y coordenadas `19.1234567`, `-99.7654321` idénticas en POST, base y GET de detalle |
| Dirección manual | Ubicación de 255 caracteres aceptada; coordenadas `null` y `null` conservadas |

## 9. H4 — fecha del objeto — 16 de octubre de 2026

La API acepta fechas ISO pasadas y la fecha actual, rechaza fecha ausente,
valores que no cumplen `yyyy-MM-dd`, fechas de calendario imposibles y fechas
futuras con 400 antes de invocar el servicio. El error del parser identifica
`fechaObjeto` y documenta el formato esperado. En integración se envió una fecha
ISO pasada y se verificó idéntica en la respuesta POST, columna `fecha_objeto`
y detalle GET.

| Ejecución | Resultado |
| --- | --- |
| `mvn clean -Dtest=ObjetoControllerTest,ObjetoRequestValidationTest,ObjetoServiceTest,ObjetoPublicacionIntegrationTest test` | 62 pruebas: 0 fallos, 0 errores |
| `npm test -- --run src/pages/Publicar.test.jsx` | 19 pruebas: todas pasaron; fecha futura bloqueada en el formulario |
