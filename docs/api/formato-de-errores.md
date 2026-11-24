# Formato de error de las dos APIs

Cambio del 23/11/2026, pedido por el equipo: la web y Android tenían que leer el error de dos formas, porque
Spring responde con `mensaje` y Django con `detail` (o con errores por campo, o con una lista).

## Qué se puede leer siempre

Toda respuesta de error (código `4xx` o `5xx`) de `/api/` en **Spring y en Django** trae:

| Campo | Contenido |
| --- | --- |
| `status` | El código HTTP (`400`, `401`, `403`, `404`, `405`, `409`, `429`…) |
| `mensaje` | Un texto en español para mostrar a la persona |

```json
{ "status": 403, "mensaje": "No autorizado: se requiere rol ADMIN", "detail": "No autorizado: se requiere rol ADMIN" }
```

## Lo que no cambió (para no romper nada)

- **Django sigue enviando `detail`** y los errores por campo tal como antes (`{"nombre": ["..."]}`). El campo
  `mensaje` resume el primero: `"nombre: Asegúrese de que este campo tenga al menos 3 caracteres."`.
- **Spring** no cambia: ya respondía `status` y `mensaje` (además de `timestamp`, `error` y `path`).
- Las respuestas correctas (`2xx`) no cambian en ninguna de las dos.

## Casos especiales de Django

- Un error que DRF enviaba como **lista** (`["Filtros admitidos: …"]`) ahora llega como objeto:
  `{"detail": "...", "mensaje": "...", "status": 400, "errores": ["..."]}`.
- Si el error es de un campo que se llama `mensaje` (por ejemplo, el mensaje de un envío masivo, H69), `mensaje`
  queda como texto y el error del campo va en `errores.mensaje`.
- El `404` de un registro que no existe venía en inglés (`"No Campana matches the given query."`); ahora es
  `"No encontrado."`.

## Cómo está hecho

Un middleware de Django (`nucleo/formato_errores.py`) agrega `status` y `mensaje` a toda respuesta de error
JSON bajo `/api/`. Así cubre las excepciones y también las ~70 respuestas de error armadas a mano en las vistas.

## Verificación del 23/11/2026

- Django: 9 pruebas nuevas en `test_formato_errores.py` que leen el JSON enviado (401, 403, 404 en español, 405,
  errores por campo, lista convertida, respuesta armada a mano, campo llamado `mensaje`, respuestas correctas
  sin cambios y rutas fuera de `/api/` sin tocar). Suite completa: **212 pruebas, OK**.
- Con las dos APIs reales (QA): Spring y Django responden `status` y `mensaje` en `401`, `403`, `404`, `400` por
  campo y `400` de lista; Django conserva `detail`; una respuesta correcta no trae `mensaje`.

## Lo que pueden simplificar la web y Android

Leer `mensaje` en las dos APIs. El código actual (web: `detail || mensaje`; Android: `mensaje` y `detail` en
400/404/409) sigue funcionando igual; no es obligatorio cambiarlo.
