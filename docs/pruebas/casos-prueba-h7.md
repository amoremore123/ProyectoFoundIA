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
