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
| API real con proveedor de IA | — | **Pendiente**: requiere `IA_API_KEY` |

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

## Pendiente

Configurar `IA_API_KEY` en el `.env` del servidor (nunca en Git), reconstruir `api` y volver
a ejecutar el script: el informe JSON indicará `fuente = ia` y la tasa de aciertos de la IA
frente al motor local.
