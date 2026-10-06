# Casos de prueba — H7 Filtrar objetos por categoría

**Responsable:** Integrante 2 · **Enfoque:** Filtros de búsqueda

## 1. Pruebas automáticas (backend Spring Boot)

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `service/ObjetoServiceTest` | Unitaria (JUnit 5 + Mockito) | Filtro por categoría y categoría inexistente |
| `controller/ObjetoControllerTest` | Endpoints (MockMvc) | Códigos HTTP de los 3 endpoints con `categoriaId` |

### Cómo ejecutarlas

```powershell
cd backend\springboot
mvn test
```

Resultado esperado: `BUILD SUCCESS`, 0 fallos.

## 2. Casos de prueba

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H7-01 | Filtro por categoría (endpoint dedicado) | `GET /api/objetos/categoria/3` | 200 y solo objetos de esa categoría | `porCategoria` (controller y service) |
| CP-H7-02 | Filtro en el listado general | `GET /api/objetos?categoriaId=3` | 200, el parámetro llega al servicio | `listarPorCategoria`, `listarFiltrandoPorCategoria` |
| CP-H7-03 | Filtro combinado con búsqueda | `GET /api/objetos/buscar?categoriaId=3` | 200, la búsqueda recibe la categoría | `buscarPorCategoria` |
| CP-H7-04 | Categoría inexistente | `GET /api/objetos/categoria/99999` | 404 "Categoría no encontrada con id 99999", no consulta los objetos | `porCategoriaInexistente` |
| CP-H7-05 | Selector en la página de inicio | elegir "Mochila" en el desplegable y buscar | La lista de resultados solo muestra esa categoría | Manual |
| CP-H7-06 | Cambiar filtro en resultados | cambiar la categoría en `/buscar` | Los resultados se actualizan sin recargar la página | Manual |

## 3. Prueba manual de punta a punta

1. Abrir http://localhost:5173/ y elegir una categoría en el buscador del hero.
2. Los resultados (/buscar) deben mostrar solo objetos de esa categoría.
3. Cambiar el desplegable de categoría en la página de resultados → la lista se actualiza.
4. Probar `curl "http://localhost:8081/api/objetos/categoria/3"` → JSON solo de esa categoría.
