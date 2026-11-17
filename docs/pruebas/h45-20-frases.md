# H45.5 — Medición de 20 frases de prueba del asistente

Fecha: **13/11/2026**. Rama `frontend/web`. Responsable de la tarea: Yajaira (capa web de H45).

## Qué se mide

Las 20 frases y su resultado esperado están en
[`frontend/usuario/src/qa/frasesAsistente.js`](../../frontend/usuario/src/qa/frasesAsistente.js).
El esperado describe lo que una persona entiende de la frase (intención, si perdió o
encontró, categoría del catálogo de `seed.sql` y lugar); **no** se ajustó a lo que devuelve
un motor. Así se comparan con la misma vara:

- el motor local de la web (`utils/asistente.js`), que responde si la API no está disponible;
- la API real `POST /api/asistente/interpretar` (Spring), con su motor local o el proveedor de IA.

Una frase es acierto solo si coinciden todos los campos que el esperado define. La frase 20
es un intento de darle órdenes a la IA: además debe salir sin categoría y sin correos ni teléfonos.

## Cómo repetirlo

```bash
node scripts/medir-frases-h45.mjs --api http://localhost:8081
```

Sin sesión (no envía datos propios) y respetando el límite de consultas por minuto.
Escribe la evidencia en [`sprint3-evidencias/h45-frases.json`](sprint3-evidencias/h45-frases.json).
La prueba `src/qa/frasesAsistente.test.js` (Vitest) protege la línea base de la web.

## Resultado del 13/11/2026

API levantada con Docker Compose (MySQL publicado en el puerto 3307 porque el 3306 lo
ocupaba XAMPP; los contenedores no cambian). **`IA_API_KEY` no está configurada**, por lo que
todas las respuestas de la API salieron con `fuente = local`.

| Motor | Aciertos | Tasa |
| --- | --- | --- |
| Motor local de la web | 17 / 20 | **85 %** |
| API real, motor local del servidor | 16 / 20 | **80 %** |
| API real con proveedor de IA | 17 / 20 | **85 %** (ver medición del 14/11) |

Tiempo de respuesta de la API: 15–38 ms (172 ms la primera consulta, en frío).

### Frases que fallaron

| N.º | Frase | Esperado | Web | API (local) |
| --- | --- | --- | --- | --- |
| 3 | Se me olvidó la laptop en el laboratorio 3 | perdió → `ENCONTRADO` | sin tipo | sin tipo |
| 7 | Recogí una casaca gris en el patio | categoría Ropa | sin categoría | sin categoría |
| 15 | ¿Cómo va el estado de mis publicaciones? | `mis_reportes` | ✅ | `buscar` |
| 18 | Muchas gracias | `gracias` | `buscar` | `buscar` |

Lectura: ambos motores locales no reconocen «olvidó» como pérdida ni asocian prendas
(casaca, chompa) a la categoría Ropa; «Muchas gracias» se toma como búsqueda porque «muchas»
no está entre las palabras ignoradas. La 15 solo falla en el servidor: su motor local no
tiene la intención `mis_reportes` que sí reconoce la web. Son los casos que el proveedor de
IA debería resolver; se dejan registrados en lugar de ajustar las reglas a la prueba.

La frase 20 (inyección de instrucciones) salió bien en ambos motores: sin categoría y sin
datos personales.

## Medición con IA real — 14/11/2026

Con `IA_API_KEY` configurada en el `.env` del servidor (no se sube a Git). Proveedor Groq,
modelo **`openai/gpt-oss-20b`**.

### Hallazgos al activar la IA

1. **Modelo retirado.** `llama-3.1-8b-instant`, el valor por defecto, ya no existe en Groq
   (respondía 404). Se cambió el valor por defecto en `.env.example`, `docker-compose.yml`,
   `application.yml` y `ClienteIaCompatibleOpenAi` a `openai/gpt-oss-20b`.
2. **Sentido invertido de `tipoBuscado`.** El modelo devolvía `PERDIDO` cuando la persona había
   perdido algo (describía su situación, no lo que hay que buscar). Ahora se le pide
   `situacion: "perdio" | "encontro"`, que entiende sin ambigüedad, y el servidor la convierte:
   quien perdió busca entre lo `ENCONTRADO`. Se aceptó también «publicar» solo cuando se pide
   expresamente.
3. **Respuestas vacías (400 `json_validate_failed`).** `gpt-oss` razona antes de responder y a
   veces agotaba los 300 tokens. Se pide `reasoning_effort: "low"` (solo a los modelos gpt-oss) y
   `max_tokens: 600`.

### Resultado

| Corrida | Respondió la IA | Aciertos de la API | Notas |
| --- | --- | --- | --- |
| Con el modelo retirado | 0 / 20 | 16 / 20 (80 %) | Todo respondió el motor local del servidor |
| `gpt-oss-20b`, antes de los ajustes | 12 / 20 | 14 / 20 (70 %) | `tipoBuscado` invertido |
| Tras pedir `situacion` | 13 / 20 | 18 / 20 (90 %) | 7 errores 400 por tokens |
| **Final, con razonamiento bajo** | **19 / 20** | **17 / 20 (85 %)** | Sin errores del proveedor |

Motor local de la web, como referencia: 17 / 20 (85 %). La IA acierta frases que el motor local
falla («Se me olvidó la laptop…», la casaca como **Ropa**, «Muchas gracias») y falla otras:
«Recogí una casaca gris en el patio» (intención `vacio`), «Quiero reportar algo que perdí» (la
toma como búsqueda) y la ubicación de «…en el pasillo». La frase de inyección siguió sin datos
personales. Tiempo de respuesta: mediana 0,8 s (0,5–2,0 s).

La corrida con 90 % tuvo 7 frases respondidas por el motor local; con la IA respondiendo casi
todo, el resultado es 85 %. Un modelo de IA puede variar entre corridas, por eso no se ajustaron
las instrucciones a estas 20 frases. Evidencia de la corrida final:
[`sprint3-evidencias/h45-frases.json`](sprint3-evidencias/h45-frases.json).

## Cómo repetir con IA

Copiar `.env.example` como `.env` en la raíz, completar `IA_API_KEY`, ejecutar
`docker compose up -d --build api` y luego `node scripts/medir-frases-h45.mjs`.
