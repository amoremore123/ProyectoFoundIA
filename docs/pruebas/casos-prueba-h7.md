# Casos de prueba — H7 Filtrar objetos por categoría

**Responsable:** Leonel · **Enfoque:** Filtros de búsqueda

Se conservan los casos CP-H7-01 a CP-H7-06 y las pruebas unitarias previas aportadas por Amore. Esta ampliación documenta las mejoras de H7 a cargo de Leonel.

## 1. Pruebas automáticas (backend Spring Boot)

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `service/ObjetoServiceTest` | Unitaria (JUnit 5 + Mockito) | Filtro por categoría y categoría inexistente |
| `controller/ObjetoControllerTest` | Endpoints (MockMvc) | Códigos HTTP de los 3 endpoints con `categoriaId` |
| `service/BusquedaObjetosIntegrationTest` | Integración (Spring Boot + H2 + MockMvc) | Filtros reales, visibilidad pública, categorías desactivadas y validación uniforme de categorías inexistentes |

### Cómo ejecutarlas

```powershell
cd backend\springboot
mvn test
```

Resultado esperado: `BUILD SUCCESS`, 0 fallos.

### Pruebas automáticas del frontend React

Ubicación: `frontend/usuario/src/pages/`

| Archivo | Tipo | Qué cubre |
| ------- | ---- | --------- |
| `Buscar.test.jsx` | Componente (Vitest + Testing Library) | Cambio de categoría, filtros combinados, opción "Todas", limpieza, enlaces, catálogo y errores independientes |
| `Inicio.test.jsx` | Componente (Vitest + Testing Library) | Selector de categoría en Inicio, categorías desactivadas y reintento del catálogo |

En otra terminal, desde la raíz del proyecto:

```powershell
cd frontend\usuario
npm ci
npm test
npm run build
```

Resultado esperado: pruebas sin fallos y compilación correcta. La suite backend usa H2 temporal y las pruebas React simulan la API; la verificación MySQL se realizó aparte.

## 2. Casos de prueba

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H7-01 | Filtro por categoría (endpoint dedicado) | `GET /api/objetos/categoria/3` | 200 y solo objetos de esa categoría | `porCategoria` (controller y service) |
| CP-H7-02 | Filtro en el listado general | `GET /api/objetos?categoriaId=3` | 200, el parámetro llega al servicio | `listarPorCategoria`, `listarFiltrandoPorCategoria` |
| CP-H7-03 | Filtro combinado con búsqueda | `GET /api/objetos/buscar?categoriaId=3` | 200, la búsqueda recibe la categoría | `buscarPorCategoria` |
| CP-H7-04 | Categoría inexistente | `GET /api/objetos/categoria/99999` | 404 "Categoría no encontrada con id 99999", no consulta los objetos | `porCategoriaInexistente` |
| CP-H7-05 | Selector en la página de inicio | elegir "Mochila" en el desplegable y buscar | La lista de resultados solo muestra esa categoría | Manual |
| CP-H7-06 | Cambiar filtro en resultados | cambiar la categoría en `/buscar` | Los resultados se actualizan sin recargar la página | Manual |
| CP-H7-07 | Categoría inexistente en los tres filtros | `categoriaId=99999` en listado/búsqueda y `/categoria/99999` | 404 "Categoría no encontrada con id 99999" en los tres endpoints | `categoriaInexistente`, `endpointsCategoriaInexistente` |
| CP-H7-08 | Categoría con formato incorrecto | `categoriaId=texto` | 400, no 500 | `parametrosInvalidos` |
| CP-H7-09 | Categoría desactivada con publicaciones anteriores | categoría con `estado=false` | Sigue en el catálogo, marcada "(desactivada)" en búsqueda e Inicio; permite consultar publicaciones públicas anteriores | `categoriaDesactivada`, `Buscar.test.jsx`, `Inicio.test.jsx` |
| CP-H7-10 | Combinar categoría, texto y tipo | categoría Mochila, `q=mochila`, tipo ENCONTRADO | Solo publicaciones que cumplen todos los criterios; cambiar categoría conserva texto y tipo | `filtrosCombinados`, `endpointPublico`, `Buscar.test.jsx` |
| CP-H7-11 | Categoría existente sin publicaciones | elegir una categoría vacía | API devuelve 200 y `[]`; la pantalla muestra "Sin resultados para esa búsqueda.", no un error | Verificación API/navegador con MySQL |
| CP-H7-12 | Volver a "Todas" | quitar solo la categoría | Se elimina `categoriaId` de la URL y se mantienen los demás filtros | `Buscar.test.jsx` |
| CP-H7-13 | Limpiar filtros | texto, categoría, tipo y ubicación aplicados | Se eliminan los cuatro filtros de la URL y el formulario; otros parámetros de la URL se conservan | `Buscar.test.jsx` |
| CP-H7-14 | Error al cargar el catálogo | falla `/api/categorias`; pulsar "Reintentar" al recuperarse | Selector deshabilitado durante carga/error; los resultados siguen independientes; el reintento recupera solo categorías | `Buscar.test.jsx`, `Inicio.test.jsx` |
| CP-H7-15 | Enlace con categoría inexistente | abrir `/buscar?categoriaId=99999` | Error visible y opción "Categoría no disponible"; "Limpiar filtros" permite recuperarse | `Buscar.test.jsx` |
| CP-H7-16 | Objetos no públicos en una categoría | publicaciones ACTIVO, RECUPERADO, OCULTO y ELIMINADO | Solo ACTIVO y RECUPERADO; orden por fecha e id descendentes | `porCategoria`, `consultasPublicas`, `estadoOcultoNoPermiteAcceso` |
| CP-H7-17 | Historial de navegación | cambiar texto/categoría y pulsar Atrás o Adelante | Selector, texto y resultados corresponden a los filtros de la URL | `Buscar.test.jsx` |

Desactivar una categoría no significa eliminarla ni permitir una categoría inexistente. Esta regla se aplica a consultas; no cambia el formulario ni las reglas de publicación.

## 3. Prueba manual de punta a punta

Preparación: API y frontend funcionando y datos ficticios en una base de pruebas. El id `3` es un ejemplo; usar el id de una categoría disponible en esa base.

1. Abrir http://localhost:5173/ y elegir una categoría en el buscador del hero.
2. Los resultados (/buscar) deben mostrar solo objetos de esa categoría.
3. Cambiar el desplegable de categoría en la página de resultados → la lista se actualiza.
4. Probar `curl "http://localhost:8081/api/objetos/categoria/3"` → JSON solo de esa categoría.
5. Combinar la categoría con texto y tipo → todos los criterios se mantienen. Elegir "Todas" → solo se quita la categoría.
6. En la base de pruebas, disponer de una categoría desactivada con publicaciones previas → se muestra "(desactivada)" y sigue siendo consultable.
7. Elegir una categoría existente sin publicaciones → estado vacío, sin error.
8. Abrir `/buscar?categoriaId=99999`, con un id inexistente → error 404 visible; limpiar filtros recupera la lista.
9. Interrumpir temporalmente la API de pruebas y recargar `/buscar` para comprobar el error del catálogo; recuperarla y reintentar → el selector vuelve a habilitarse. Reintentar la búsqueda por separado.
10. Pulsar "Limpiar filtros" → no quedan texto, categoría, tipo ni ubicación aplicados.
11. Comprobar que las publicaciones OCULTO/ELIMINADO no se muestran al filtrar y que Atrás/Adelante restaura la categoría aplicada.

## 4. Resultados verificados

**Fecha:** 2026-10-05 · **Código probado:** `56b522f`, rama `feature/h6-h7-busqueda`.

La suite compartida H6/H7 pasó sus 25 casos también con MySQL 8.0.46 y el esquema `database/script.sql`, en un contenedor aislado. Además, pasaron las 35 comprobaciones de API y los 12 casos de navegador que cubren ambas historias.

El detalle de resultados, comandos de pruebas y límites de la verificación está en [H6 — Resultados verificados](casos-prueba-h6.md#4-resultados-verificados). No se modificaron datos existentes ni las historias de registro/login o publicación; estos resultados no sustituyen el cierre formal por el equipo.

## 5. Validación integral del PDF — 17 de octubre de 2026

Se empezó H7 después de ejecutar H6 y registrarla en el commit local `39b5faf`.
La base integrada es `cafdf93`: backend/datos de Amore/Yajaira (`5cfb391`) y
frontend completo de `frontend/web` (`dd3e72b`), sin push ni cambios en otras ramas.

### Refuerzo versionado

| Prueba | Qué comprueba |
| --- | --- |
| `categoriaVaciaEnLosTresFiltros` (2 entradas) | Categoría activa/desactivada sin publicaciones: sigue en catálogo con ID/estado, 200 y `[]` en listado, búsqueda y endpoint dedicado. |
| `relacionCategoriaEnLosTresFiltros` (3 entradas) | Mismos objetos, orden, ID y nombre de categoría en los tres endpoints; solo publicaciones públicas. |
| `ordenVisualNoCambiaIds` | El catálogo alfabético no renumera IDs ni altera la relación de las publicaciones. |
| `todasConservaTextoTipoYVisibilidad` | Quitar categoría conserva texto/tipo y admite coincidencias públicas de otra categoría, sin sumar filtros ni exponer privados. |
| `formatoCategoriaEnLosTresFiltros` (3 entradas) | Texto, decimal y desbordamiento devuelven 400 en los tres filtros. |
| `inexistenteNoSeConfundeConVacia` (3 entradas) | 0, negativo y máximo inexistentes devuelven 404, incluso si los otros filtros no tienen coincidencias. |
| `CategoriaControllerTest` (3 casos) | IDs/estado del catálogo, vacío real y fallo 500 con recuperación. |
| `Buscar.test.jsx` (5 entradas nuevas) | Escribir `mo`/`MÓ`, filtrar opciones sin cambiar IDs, incluir desactivadas, conservar selección/URL sin coincidencias, borrar/limpiar y reintentar catálogo conservando filtros y resultados. |
| `ImagenObjeto.test.jsx` (3 casos nuevos) | Imagen válida, ausente y fallida; Sin foto distingue la referencia y una URL nueva puede recuperarse. |

Se reutiliza la lógica de categorías existente y la búsqueda de opciones de
Yajaira; no se presentan pruebas nuevas como cambios de lógica de producción.
Se mantienen las pruebas previas de Todas, texto + categoría + tipo, historial,
cancelación y reintentos independientes.

### Hallazgos corregidos antes de entregar

- La primera ejecución integrada midió **4,389:1** en el contador de resultados
  en tema claro. Se cambió `.resultados-contador` de `--text-soft` a
  `--text-muted`, sin alterar búsqueda, filtros ni la paleta global.
- El caso de imagen fallida encontró dos textos `Sin foto` (placeholder y
  etiqueta). Era una ambigüedad del selector automatizado: se acotó a la etiqueta
  del contenedor de imagen; no se cambió la aplicación para ocultar ese fallo.
- Se conserva la ejecución inicial con **89/91** comprobaciones correctas,
  incluidos los dos fallos, en `evidencias/h7-primera-ejecucion/`.
  El flujo real de registro hasta categoría sí pasó en ese intento. Para repetir
  se retiraron únicamente su cuenta y publicación QA, conservando fixtures y
  evidencias, y se repiten suites/build y la integración tras la corrección.

### Resultados nuevos

| Nivel | Resultado nuevo |
| --- | --- |
| `mvn.cmd -B -ntp clean verify` | 189/189, sin fallos, errores ni omitidas; JAR generado. Incluye H6 y las demás historias del backend. |
| `BusquedaObjetosIntegrationTest` | 57 casos correctos; 13 entradas nuevas de H7 sobre la fase H6. |
| `CategoriaControllerTest` | 3 casos nuevos correctos. |
| `npm ci` y `npm test` de usuario | 95/95, sin omitidas; 5 entradas de categorías y 3 casos de imágenes añadidos después de H6. |
| `npm run build` | Usuario y administrador correctos. El administrador no tiene suite de tests configurada. |
| API/MySQL reales, ejecución final | **78/78 comprobaciones correctas**, incluida regresión de búsquedas H6, catálogo ordenado, relación categoría/publicación en los tres filtros, vacías, desactivadas, inexistentes y formatos inválidos, sin huérfanos ni privados. |
| Chrome real sobre el build final | **13/13 casos agrupados correctos**: H6, opciones al escribir, selección y teclado, Todas, estados de categoría, catálogo independiente, fallo de conexión/HTTP, imágenes, tamaños/temas, contraste y flujo completo. |
| Contraste del contador tras la corrección | Claro **5,94:1** y oscuro **8,83:1**. Se midieron también título, etiquetas, botones, estado y textos de detalle en ambos temas: todos los textos comprobados superan 4,5:1. No es una auditoría WCAG completa. |

La repetición posterior a la corrección pasó otra vez las 189 pruebas backend y
95 frontend y generó JAR/build correctos, antes de repetir las 91 comprobaciones
integradas. No se cerró H7 con los fallos de la primera ejecución.

### Evidencia de integración exigida por el PDF

- Catálogo con IDs **101/205/309/413**, distintos de sus posiciones visuales.
  Se comprueban objetos e IDs completos, no solo el contador en pantalla.
- Opciones ordenadas; escribir texto parcial/mayúsculas/tildes reduce opciones.
  Cero coincidencias conserva la categoría aplicada; borrar restaura el catálogo.
  Se eligió una opción con teclado real. Todas solo retira `categoriaId` y
  conserva texto, tipo y ubicación en URL; recarga y Atrás/Adelante los restauran.
- Vacía devuelve 200/estado vacío; desactivada conserva el historial consultable;
  inexistente produce 404 y permite limpiar. No aparecen OCULTO/ELIMINADO.
- Fallo 503 controlado solo del catálogo: se deshabilita el selector, siguen los
  resultados y el reintento recupera catálogo/ID/filtros sin recargar. Un fallo de
  red solo de búsqueda y un 500 tienen su reintento independiente, en español y
  sin confundirse con Sin resultados. La caída **real de MySQL QA** da 500; al
  recuperarlo, API y búsquedas vuelven sin reiniciar Java.
- **Flujo completo en navegador, no solo HTTP:** registro por formulario → SMTP
  real capturado en Mailpit → código recibido y verificación por pantalla → login
  con JWT → publicación con ubicación manual y fecha → detalle → búsqueda →
  categoría. Se comprueban hash BCrypt, verificación e invalidación del código;
  ubicación, fecha, categoría y descripción UTF-8/emoji coinciden en UI, API y MySQL.
- Imagen válida decodificada desde el build, ausente y fallida desde API/datos;
  Sin foto permanece visible. Sin desborde a 320/375/1280 px en claro/oscuro;
  capturas, encabezados y ausencia de excepciones JavaScript comprobados.

Evidencia local: `C:\Users\Leonel\Downloads\FoundIA-Leonel-Integral-20261017\`.
Se incluyen `evidencias/h7-backend.json`, `h7-surefire/`,
`evidencias/h7-usuario.json`, logs de Maven/builds, `evidencias/h7-fuentes.json`,
`evidencias/h7-integral-casos.json`, `evidencias/h7-integral-resumen.json` y
`evidencias/h7-capturas/`, además de los scripts/fixtures/configuración QA.
Los JSON contienen esperado/obtenido/estado; las capturas incluyen errores controlados,
temas/tamaños y detalle/categoría del flujo. Los 13 casos de navegador agrupan
varias interacciones y aserciones, no son 13 historias diferentes.

La suite backend creció de 154 a 173 en H6 y a 189 en H7; frontend pasó de 80
en la integración a 87 en H6 y 95 en H7. No se suman ejecuciones solapadas ni se
atribuyen todas las pruebas a Leonel. Los resultados históricos se conservan
separados, sin presentarlos como prueba del código actual.

**Resultado H7:** validada en backend, datos y la integración web exigida en esta
historia, junto con H6 y el flujo indicado. No se declara cerrado todo el Sprint
1 ni se abren H8/H9/H10/H16/H17: el PDF exige completar además la matriz de las
otras historias/pantallas de la primera parte.
