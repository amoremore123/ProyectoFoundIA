# H49 — Sugerir categoría y descripción con IA al publicar

> Estado: API (Spring) y web implementadas y probadas. La capa Android (Amore) consume este contrato.

La IA **solo sugiere**: el servidor no guarda nada y la persona decide si acepta, edita o descarta.
Sin clave de IA (o si falla) responde el motor local, así Publicar funciona igual.

## `POST /api/asistente/sugerir-publicacion`

Requiere sesión (`Authorization: Bearer <token>`), igual que Publicar.

```json
{ "nombre": "mochila", "descripcion": "mochila negra con llavero rojo", "tipo": "PERDIDO" }
```

| Campo | Regla |
| --- | --- |
| `nombre` | opcional, hasta 150 caracteres |
| `descripcion` | opcional, hasta 1000 caracteres |
| `tipo` | `PERDIDO`, `ENCONTRADO` u omitido |

Respuesta `200`:

```json
{
  "categoria": { "id": 3, "nombre": "Mochila" },
  "descripcion": "Mochila negra con llavero rojo.",
  "origen": "IA",
  "aviso": "Es solo una sugerencia: revísala antes de publicar."
}
```

- `categoria`: categoría **activa** del catálogo o `null`. El nombre sale de la base, nunca del modelo.
- `descripcion`: texto de 10 a 500 caracteres o `null`. Se descarta si trae enlaces, formato (markdown, `<>`), datos personales u ofensas.
- `origen`: `IA` o `LOCAL`.

Errores: `400` texto vacío o casi vacío, palabras ofensivas o campos demasiado largos; `401` sin sesión; `429` límite de uso.

## Seguridad frente a la IA

- El texto se envía delimitado (`"""…"""`) y con la orden de ignorar instrucciones que aparezcan dentro.
- Antes de enviarlo se quitan correos, teléfonos y otros datos personales (`LimpiadorDatosPersonales`).
- Si el modelo responde fuera del contrato JSON, se ignora y se usa el motor local.

## Pruebas

- Spring: `SugerenciaPublicacionServiceTest` (7) y `SugerenciaPublicacionIntegrationTest` (3): sugerencia válida, categoría inactiva o inexistente, enlaces y formato, respuesta fuera de contrato, IA caída o sin clave, texto vacío u ofensivo sin llamar a la IA, datos personales e intento de dar órdenes, sin sesión, nada se guarda.
- Web: `PublicarSugerencia.test.jsx` (4): no cambia nada hasta aceptar, editar antes de usar, descartar, categoría desactivada ignorada, error con publicación igual.
