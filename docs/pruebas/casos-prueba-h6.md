# Casos de prueba — H6 Buscar objetos

**Responsable:** Leonel · **Enfoque:** Búsqueda de objetos y resultados públicos

## 1. Pruebas automáticas

### Backend Spring Boot

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `service/BusquedaObjetosIntegrationTest` | Integración (Spring Boot + H2 + MockMvc) | Búsqueda textual, filtros combinados, orden, visibilidad pública y respuestas HTTP de H6/H7 |

### Frontend React

Ubicación: `frontend/usuario/src/pages/`

| Archivo | Tipo | Qué cubre |
| ------- | ---- | --------- |
| `Buscar.test.jsx` | Componente (Vitest + Testing Library) | Texto y filtros en la URL, navegación, cancelación de consultas, estados vacíos, errores y reintentos |
| `Inicio.test.jsx` | Componente (Vitest + Testing Library) | Envío del texto, categoría y ubicación desde Inicio |

### Cómo ejecutarlas

Desde la raíz del proyecto:

```powershell
cd backend\springboot
mvn test
```

En otra terminal, desde la raíz del proyecto:

```powershell
cd frontend\usuario
npm ci
npm test
npm run build
```

Resultado esperado: `BUILD SUCCESS`, pruebas web sin fallos y compilación de React correcta.

La suite de integración del repositorio utiliza H2 temporal; no necesita MySQL ni modifica una base existente. Las pruebas React simulan las respuestas de la API. La verificación con MySQL y navegador real se describe en la sección 4.

## 2. Casos de prueba — H6 Buscar objetos

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H6-01 | Buscar por parte del nombre | `q=negra` para "Mochila negra" | 200 y publicaciones públicas cuyo nombre contiene el texto | `busquedaTextual` |
| CP-H6-02 | Buscar por descripción | `q=cuaderno` | 200 y publicaciones cuya descripción contiene el texto | `busquedaTextual` |
| CP-H6-03 | Buscar por ubicación | `q=biblioteca` | 200 y publicaciones cuya ubicación contiene el texto | `busquedaTextual` |
| CP-H6-04 | Mayúsculas y espacios externos | `q=  NEGRA  ` | Mismos resultados que `q=negra` | `busquedaTextual`, `Buscar.test.jsx` |
| CP-H6-05 | Buscar sin texto | sin `q`, texto vacío o solo espacios | 200 y todas las publicaciones públicas que cumplen los demás filtros | `sinTexto` |
| CP-H6-06 | Caracteres literales | `100%`, `A_B`, `Aviso!` | `%`, `_` y `!` se buscan como parte del texto, no como comodines | `comodinesLiterales` |
| CP-H6-07 | Combinar texto, tipo y categoría | `q=mochila`, categoría Mochila y tipo ENCONTRADO | Solo objetos que cumplen los tres criterios a la vez | `filtrosCombinados`, `endpointPublico`, `Buscar.test.jsx` |
| CP-H6-08 | Texto sin coincidencias | `q=no-existe-987` | API devuelve 200 y `[]`; la pantalla muestra "Sin resultados para esa búsqueda." | `sinResultados`, `Buscar.test.jsx` |
| CP-H6-09 | No exponer objetos ocultos o eliminados | consultas públicas, incluso con `estado=OCULTO` | Solo ACTIVO y RECUPERADO; pedir OCULTO o ELIMINADO devuelve `[]` | `consultasPublicas`, `estadoOcultoNoPermiteAcceso`, `otrosFiltrosPublicos` |
| CP-H6-10 | Detalle no público | detalle o coincidencias de un objeto OCULTO o ELIMINADO | 404; no se devuelve la publicación | `detalleNoPublico`, `endpointOculto` |
| CP-H6-11 | Orden estable | publicaciones con la misma `fechaPublicacion` | Fecha descendente y, en empates, id descendente | `sinTexto`, `porCategoria` |
| CP-H6-12 | Entrada desde Inicio y enlace compartido | texto, categoría y ubicación; abrir la URL de resultados | Se conservan los filtros aplicados; Atrás y Adelante restauran el formulario | `Inicio.test.jsx`, `Buscar.test.jsx` |
| CP-H6-13 | Respuesta anterior lenta | cambiar de filtro antes de terminar la consulta anterior | La consulta anterior se cancela y no reemplaza resultados ni errores actuales | `Buscar.test.jsx` |
| CP-H6-14 | Error al buscar y reintento | API no disponible; pulsar "Reintentar" al recuperarse | Error visible, sin falso estado vacío; el reintento carga los resultados | `Buscar.test.jsx` |
| CP-H6-15 | Buscar de nuevo el mismo texto | pulsar "Buscar" sin cambiar el texto aplicado | Se realiza una consulta nueva | `Buscar.test.jsx` |
| CP-H6-16 | Abrir el resultado | pulsar la tarjeta de una publicación | Se abre `/objeto/{id}` con su detalle | `Buscar.test.jsx` y navegador real |
| CP-H6-17 | Tipo con formato incorrecto | `tipo=OTRO` | 400, no 500 | `parametrosInvalidos` |

El filtro `ubicacion` de la URL de la pantalla se aplica en React sobre la lista devuelta. No es un parámetro independiente de `/api/objetos/buscar`; `q` sí busca también en la ubicación.

## 3. Prueba manual de punta a punta

Preparación: backend y frontend funcionando, categorías disponibles y publicaciones ficticias. Para comprobar estados OCULTO/ELIMINADO y caracteres especiales, usar una base de pruebas, no datos del equipo.

Con Docker Desktop iniciado, desde la raíz del proyecto:

```powershell
docker compose up -d --build mysql mailpit api frontend-usuario
```

No es necesario borrar volúmenes. `database/script.sql` y `database/seed.sql` contienen operaciones de limpieza; no ejecutarlos sobre una base que deba conservarse.

1. Abrir http://localhost:5173/ y buscar `  moch  ` → la URL conserva `q=moch` y aparecen coincidencias públicas.
2. Buscar un texto presente solo en la descripción y otro presente en la ubicación → ambos encuentran la publicación correspondiente.
3. Combinar texto, categoría y tipo en `/buscar` → los resultados cumplen todos los filtros. La ubicación enviada desde Inicio también se conserva.
4. Pulsar Atrás y Adelante → el texto, el selector y el tipo coinciden con la URL.
5. Buscar `%` en una base con "Estuche 100% transparente" y otros objetos → aparece solo la coincidencia literal. Repetir con `_` y `!`.
6. Buscar un texto inexistente → aparece el estado vacío, no un error.
7. Interrumpir temporalmente la API de pruebas y recargar `/buscar` → aparecen errores separados de búsqueda y catálogo. Recuperarla y reintentar cada uno → se recuperan de forma independiente.
8. Pulsar "Limpiar filtros" → se borran texto, categoría, tipo y ubicación; vuelven las publicaciones públicas.
9. Verificar que OCULTO y ELIMINADO no aparecen en la lista y que sus detalles devuelven 404.
10. Abrir una tarjeta visible → se carga el detalle de esa publicación.

## 4. Resultados verificados

**Fecha:** 2026-10-05 · **Código probado:** `56b522f`, rama `feature/h6-h7-busqueda`.

| Verificación | Resultado | Alcance |
| ------------ | --------- | ------- |
| Suite completa backend | 86 casos, 0 fallos y 0 omitidos | Incluye las pruebas anteriores del equipo; no todos los casos corresponden a H6/H7 |
| Suite completa frontend | 23 casos, 0 fallos | `Buscar.test.jsx` e `Inicio.test.jsx` |
| Integración H6/H7 con MySQL 8.0.46 | 25 casos, 0 fallos y 0 omitidos | Mismas comprobaciones de `BusquedaObjetosIntegrationTest`, repetidas contra `database/script.sql` |
| API real con MySQL | 35 comprobaciones correctas | Búsqueda, categorías, estados públicos, orden, detalle y códigos 400/404 |
| Navegador con API y MySQL reales | 12 casos correctos | Errores separados, reintentos, entrada desde Inicio, filtros, historial, limpieza, categorías desactivadas/inexistentes/vacías, búsqueda literal y detalle |
| Compilación | Spring Boot y React correctos | Jar del backend y build de React |

La comprobación MySQL se hizo con una adaptación de configuración en una copia temporal, un contenedor aislado y el esquema del proyecto. La API/navegador usaron 9 objetos ficticios y 5 categorías; las pruebas de integración usaron otra base vacía en ese contenedor. No se probaron datos ni configuración privada del equipo.

Los 25 casos repetidos en MySQL no son pruebas adicionales distintas. El adaptador, los scripts de comprobación y los servicios temporales se retiraron al terminar; el comando `mvn test` del repositorio sigue utilizando H2 para esta suite.

Estos resultados verifican la implementación probada; el cierre formal de las historias corresponde a la revisión del equipo. Los casos de categoría se detallan en [H7 — Filtrar objetos por categoría](casos-prueba-h7.md).

## 5. Validación integral del PDF — 17 de octubre de 2026

Rama de trabajo: `dev/leonel`, base backend/datos `5cfb391`. Se integró
`frontend/web` (`dd3e72b`) con autorización del usuario mediante el merge local
`cafdf93`. Se conservaron las correcciones de Amore, incluidos IDs de categorías,
historial de eliminados y descripción de 16.000 unidades UTF-16 como la API.
Esta ejecución es nueva; no reutiliza como evidencia el intento local cancelado.

### Refuerzo versionado de H6

| Prueba | Cobertura nueva |
| --- | --- |
| `fragmentosPorHttp` | Fragmentos de nombre, descripción y ubicación; mayúsculas, espacios externos y tildes, conservando el texto de respuesta. |
| `literalesPorHttp` | `%`, `_`, `!`, `!%_`, barra invertida, `A+B` y `O'Neil` en los tres campos; excluye falsas coincidencias y comprueba orden. |
| `sinTextoPorHttp` | `q` ausente, vacío, espacios y tabulación mantienen exclusivamente las publicaciones públicas y su orden. |
| `ordenPorFechaAntesDeId` | Una publicación más nueva con ID menor precede a las anteriores; el ID solo desempata fechas iguales. |
| `errorVacioYRecuperacion` | 400 de parámetro, 200 con `[]` y 200 con coincidencias son respuestas diferentes. |
| `ObjetoControllerTest.errorDeBusquedaYReintento` | Fallo 500 en español sin detalles internos; el siguiente intento recupera resultados, no un falso vacío. |
| `Buscar.test.jsx` (7 entradas nuevas) | El texto literal se conserva al enviar y decodificar la URL, junto con categoría, tipo, ubicación y parámetros ajenos. |

Se mantienen las pruebas previas de filtros combinados, privacidad, cancelación
de consultas, navegación y estados independientes de búsqueda y catálogo.
La implementación de búsqueda existente se reutiliza; no se presenta el refuerzo
de pruebas como una corrección de lógica inexistente.

### Resultados de esta ejecución

| Verificación | Resultado nuevo |
| --- | --- |
| `mvn.cmd -B -ntp clean verify` | 173/173, sin fallos, errores ni omitidas; JAR generado. Son 19 casos nuevos de H6 sobre la base de 154; la suite completa incluye otras historias. |
| `npm ci` y `npm test` del usuario | 87/87, sin omitidas; 7 entradas nuevas de H6 sobre las 80 de la interfaz integrada. |
| `npm run build` | Correcto en usuario y administrador. El administrador no tiene script de tests; no se afirma haber ejecutado una suite inexistente. |
| API/MySQL reales | 34/34 comprobaciones: fragmentos y literales en los tres campos, filtros, orden, privados excluidos, 400/404/401 y recuperación. |
| Chrome real sobre el build | 7/7 casos agrupados: 12 búsquedas literales/parciales; URL, intersección y Atrás/Adelante/recarga; error/reintento; vacío, visibilidad y detalle; encabezados, Sin foto y contraste del detalle; tamaños/temas/teclado; ausencia de excepciones JavaScript. |
| Caída controlada | Se detuvo únicamente MySQL QA: 500 con `Error interno del servidor`, no `[]`. Al recuperarlo, la consulta volvió a funcionar sin reiniciar la API. En navegador se inyectó además un 500 solo de búsqueda y se reintentó contra la API real sin perder filtros. |

Las compilaciones y tests se ejecutan sobre copias temporales, sin cargar `.env`
del equipo. Las suites del repositorio usan H2 efímera y API simulada en React;
la integración aparte usa MySQL/Mailpit y un Chrome real automatizado por
Playwright sobre el build de producción. No se reimporta SQL en la base del equipo.

Se usaron 4 categorías con IDs 101/205/309/413 y 29 publicaciones ficticias,
incluidas ocultas/eliminadas, fechas e IDs en orden distinto y literales en
nombre/descripción/ubicación. El cliente HTTP y el navegador aplicaron codificación
URL real. Se revisaron capturas a 320/375/1280 px en claro y oscuro, sin desborde
horizontal. El contraste medido de los textos de detalle fue al menos 4,5:1;
no equivale a una auditoría WCAG completa.

Herramientas: Maven 3.9.16, JDK 21.0.12.1 (destino Java 17), Node 24.21.0,
Vite 5.4.21 y Chrome local mediante Playwright. Los procesos Java/preview propios
de esta fase se terminaron y se comprobó que no quedaran abiertos.

Evidencia nueva: `C:\Users\Leonel\Downloads\FoundIA-Leonel-Integral-20261017\`.
Contiene `evidencias/h6-backend.json`, `h6-surefire/`,
`evidencias/h6-usuario.json`, logs de compilación, `evidencias/h6-fuentes.json`,
`evidencias/h6-integral-casos.json`, `evidencias/h6-integral-resumen.json` y
`evidencias/h6-capturas/`, además de los scripts y configuración QA.
Cada comprobación registra esperado, obtenido y estado. Los siete casos de
navegador contienen varias aserciones; no se suman como pruebas unitarias nuevas.

**Resultado H6:** comprobada también con el frontend integrado antes de comenzar
H7. El flujo completo registro → categoría y la regresión final se registran en
la fase H7. No se declara aprobado el resto del Sprint 1 por este resultado.
