# H8/H9/H10/H16/H17 — API y datos de Leonel

Fecha: **20 de octubre de 2026**. Rama: `dev/leonel`.
Referencia: páginas físicas 17–20 del PDF de correcciones y reparto del Lab06.
Base integrada: `d5e6cf2`, con los avances publicados de Amore y Yajaira.
Trabajo local, **sin push, sin modificar `main` ni las otras ramas**.

## Alcance y pendientes de integración

Este bloque implementa y prueba API/datos. **No declara cerradas las cinco historias completas**:
las pantallas de filtros avanzados y comparación, sus pruebas de navegador, URL/Atrás,
320/375/1280 px, teclado y contraste corresponden a Yajaira en `frontend/web`.
No se modificaron sus interfaces ni el trabajo de Perfil.

**Entrega a Amore (H18):** `ContactoService.crearCoincidencia` todavía almacena 90/65 puntos
según ubicación. Debe reutilizar `CoincidenciaService.puntuar`, `nivel` y `UMBRAL_SIMILITUD`
para alinear la aceptación y persistencia del contacto con H16/H17. No se modificó H18/H20.
El detalle público recalcula con el motor vigente y no sobrescribe una puntuación histórica.
Contactar, reclamar y confirmar una devolución no son acciones equivalentes.

## Contrato de búsqueda (H8/H9/H10)

Se conservan las rutas existentes. `/buscar`, `/ubicacion` y `/fecha` usan `BusquedaService`;
se retiraron las implementaciones anteriores duplicadas del servicio de objetos.

| Ruta GET | Comportamiento |
| --- | --- |
| `/api/objetos/buscar` | Combina todos los filtros presentes con AND en SQL. |
| `/api/objetos/ubicacion` | Mismo contrato: texto manual y/o proximidad real. |
| `/api/objetos/fecha` | Mismo contrato; ordena por fecha del objeto incluso sin rango. |

Parámetros opcionales:

| Parámetro | Regla |
| --- | --- |
| `q` | Coincidencia parcial en nombre, descripción **o** ubicación, sin distinguir mayúsculas; espacios externos ignorados y `%`, `_`, `!` literales. |
| `tipo`, `categoriaId` | Tipo exacto e ID original de categoría. Categorías vacías/desactivadas conservan las reglas H7; ID inexistente: 404. |
| `ubicacion` | Coincidencia textual parcial literal; no inventa coordenadas ni distancias. |
| `desde`, `hasta` | ISO `yyyy-MM-dd`, sobre `fechaObjeto`, ambos extremos inclusivos. Un extremo deja el otro abierto; ausentes no restringen la fecha. |
| `latitud`, `longitud` | Deben venir juntas y ser finitas; rangos −90…90 y −180…180. Ausentes/vacías permiten continuar solo con texto. |
| `radioKm` | Kilómetros, mayor que 0 y hasta 1000; requiere coordenadas. Predeterminado: 5 km. |

Formato inválido, rango invertido, coordenadas incompletas/no finitas o radio inválido:
**400 con mensaje en español**, no 500. Una búsqueda válida sin coincidencias devuelve **200 `[]`**.
Solo se devuelven objetos ACTIVO/RECUPERADO; OCULTO/ELIMINADO nunca participan.

**Proximidad:** Haversine con radio terrestre 6371 km, calculado en SQL, incluido el filtro
`distancia <= radioKm` y el orden ascendente de cercanía. Los objetos sin ambas coordenadas
se excluyen de proximidad, no de la alternativa textual. Se limita el argumento numérico a
0…1 para evitar errores de redondeo. La respuesta conserva las coordenadas del objeto;
no añade una distancia ficticia. La futura interfaz puede usar la misma fórmula para mostrarla.

**Orden:** primero cercanía cuando está activa; después `fechaObjeto` descendente si hay
rango o se usa `/fecha`; finalmente publicación e ID descendentes como desempate.
Sin proximidad ni fecha, `/buscar` mantiene el orden anterior de H6/H7.

Ejemplo de filtros combinados:

```text
GET /api/objetos/buscar?q=mochila&tipo=ENCONTRADO&categoriaId=1&ubicacion=Biblioteca&desde=2026-10-01&hasta=2026-10-20&latitud=0&longitud=0&radioKm=2
```

## Contrato de coincidencias (H16/H17)

`GET /api/objetos/{objetoId}/coincidencias` conserva los campos de la lista y añade
`coincidenciaId` de tipo **string**, siempre **`perdidoId-encontradoId`**, incluso al
consultar desde el hallazgo. **No usar `objetoId` como identificador de coincidencia.**

Solo se comparan objetos ACTIVO de tipos opuestos, misma categoría y autores distintos.
La consulta acota tipo/categoría/estado en BD y precarga categoría/autor/fotos para evitar N+1.
Una base RECUPERADO devuelve lista vacía; una base oculta/eliminada/inexistente, 404.
Se ordena por puntuación descendente e ID del candidato ascendente para empates.

| Criterio | Puntos máximos y cálculo |
| --- | --- |
| Nombre | 40 × Jaccard de palabras normalizadas, redondeado al entero más cercano. Ignora tildes, mayúsculas, puntuación y palabras repetidas. |
| Categoría | 25 si coincide el ID. Además es requisito de selección de candidatos. |
| Ubicación | 20 si el texto normalizado es igual; 10 si uno contiene al otro; 0 si falta o difiere. |
| Fecha | Diferencia absoluta: 0–3 días → 15; 4–7 → 10; 8–30 → 5; más de 30 o fecha ausente → 0. |

Total máximo: **100**. Se sugieren desde **40**: BAJA 40–54, MEDIA 55–79, ALTA 80–100.
Datos incompletos no suman puntos inventados. Es una heurística de similitud,
**no una probabilidad de propiedad ni un modelo IA entrenado**.

`GET /api/coincidencias/{coincidenciaId}` devuelve:

```json
{
  "id": "7-8",
  "perdido": { "id": 7, "fotos": [] },
  "encontrado": { "id": 8, "fotos": [] },
  "porcentaje": 100,
  "nivel": "ALTA",
  "criterios": { "nombre": 40, "categoria": 25, "ubicacion": 20, "fecha": 15 }
}
```

Los objetos del ejemplo están abreviados: se devuelven sus DTO públicos completos,
incluidas las fotos reales y el resumen público del autor de H19. No correo, contraseña,
hash ni apellido completo. Ambas consultas son públicas como el detalle de objetos.

Por compatibilidad, un ID numérico solo resuelve una fila **ya existente** de `coincidencias`;
no se interpreta como ID de objeto. Se revalidan estado, tipos, categoría, autores y umbral.
ID mal formado/inexistente o pareja ya no disponible devuelve 404 sin contenido retirado.
Los GET **no crean/actualizan coincidencias, contactos ni notificaciones**.

## Pruebas ejecutadas y evidencias

### Suite reproducible

Desde `backend/springboot`: `mvn -B -ntp verify` — **307 pruebas, 0 fallos, 0 errores, 0 omisiones**.
Cada integración usa su propia H2 temporal. Se conservan las regresiones de autenticación,
publicación, categorías, fotos, edición, perfil, privacidad, contacto, reclamo y notificaciones.

| Clase | Casos | Cobertura |
| --- | ---: | --- |
| `FiltrosCoincidenciasIntegrationTest` | 44 | Proximidad, límites, fechas, validación, niveles, parejas, visibilidad, fotos, privacidad e ID persistido. |
| `FiltrosCombinadosIntegrationTest` | 17 | Las 15 parejas posibles entre texto/categoría/tipo/ubicación/fecha/proximidad; todos juntos y ninguno. Cada control incumple exactamente un filtro. |
| `CoincidenciaCriteriosTest` | 17 | Pesos, normalización, Jaccard, datos incompletos y extremos de fecha/niveles. |
| `CoincidenciaRendimientoIntegrationTest` | 1 | 1001 publicaciones sintéticas, 500 candidatos, cinco ejecuciones; máximo 8 consultas y presupuesto QA de 5 s. |

Ejecución final H2: **147, 68, 51, 43 y 41 ms**, con **2 consultas** en la última muestra.
`verify` terminó con **BUILD SUCCESS**, incluidas compilación y generación del JAR.

### HTTP real con MySQL 8

**90 peticiones HTTP y 46 comprobaciones de datos aprobadas.** Spring en `127.0.0.1:18419`,
MySQL QA en `127.0.0.1:13349`, base nueva `foundia_leonel_20261020_061133`, `utf8mb4_unicode_ci`.
Se importó el esquema únicamente después de comprobar que esa base **no existía** y sin
sentencias DROP. No se reimportó ni migró la base del equipo. Este bloque no requiere
columnas/tablas nuevas; no acredita el estado de migraciones H11/H20 de la base compartida.

| Caso/datos | Esperado | Obtenido |
| --- | --- | --- |
| Coordenadas (0,0), objetos en longitudes .001/.01/.1 | ≈0.111/1.112/11.119 km; radio 2 solo los dos primeros, en ese orden | Correcto en `/ubicacion` y `/buscar`; radio 12 incluye el tercero. |
| Radio ≈0.1111949266 km, ±0.000001 km | Inclusión/exclusión en el límite | Correcto; sin objetos sin GPS, ocultos ni eliminados. |
| Texto `100%_!`, ubicación manual sin GPS | Literal y alternativa textual | Solo la coincidencia literal; objeto manual consultable sin distancia. |
| Fecha del objeto hoy/ayer/anteayer, alta en orden distinto | Rango inclusivo, un extremo y sin rango; fecha reciente primero | IDs y orden esperados en los cinco escenarios. |
| Todos los filtros activos / texto inexistente / entradas inválidas | AND / 200 `[]` / 400; categoría inexistente 404 | Correcto y mensajes de error separados del vacío. |
| Llaves azules vs iguales, rojas y candado; otra pareja con 35 puntos | 100 ALTA, 73 MEDIA, 40 BAJA; 35 excluida | Lista y detalle coherentes; autores iguales excluidos. |
| Fotos subidas en ambos objetos y GET repetidos | Fotos de la pareja correcta, sin escrituras ni datos privados | Imágenes descargables; recuentos de coincidencias/contactos/avisos sin cambios. |
| Ocultar/eliminar/recuperar después de generar el enlace | Detalle 404 y candidato retirado | Correcto; base recuperada devuelve `[]`. |
| Coincidencia histórica con 65 puntos | Detalle recalculado a 100, fila histórica intacta | Correcto por ID numérico; sigue almacenada con 65.00. |
| Textos con é, ñ y emoji | UTF-8 conservado en MySQL | HEX incluye `C3A9` y `F09F9880`. |

Carga HTTP/MySQL: 1001 publicaciones adicionales, 500 candidatos válidos, cinco muestras:
**242.87, 149.86, 116.34, 102.63 y 107.72 ms**; 500 resultados correctos en cada ejecución.
Son mediciones QA locales, **no un SLA de producción**.

Tras retirar las implementaciones antiguas duplicadas y reconstruir el JAR, se repitieron
**27 peticiones HTTP sobre el artefacto final**, todas correctas, sin modificar datos.
Recuentos antes/después: 2 usuarios, 1014 objetos, 1 coincidencia histórica, 2 fotos,
0 contactos, 0 notificaciones y 0 reclamos. Evidencia: `smoke-final.json`.

Se comprobaron los hashes de los **33 archivos protegidos**, sin diferencias, y el estado
de `main`, intacto. La única referencia local modificada fue `dev/leonel`.

Respaldo y evidencia cruda local:
`C:\Users\Leonel\AppData\Local\Temp\opencode\foundia-leonel-s1-20261020-061133\`.
Incluye `antes.bundle`, estado protegido inicial, scripts QA, esquema aislado,
`maven-final.log`, `http-mysql-evidencias.json`, `http-mysql-resumen.json`, `smoke-final.json`,
`proteccion-verificada.json` y `mysql-explain.txt`.
Los resultados del desarrollo previamente descartado no se usan para acreditar esta entrega.
