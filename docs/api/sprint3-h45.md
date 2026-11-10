# H45 — Asistente de IA personalizado

> Estado de esta entrega: API implementada y probada en Spring. El proveedor
> Claude es **opcional y queda pendiente de confirmación del docente**; por
> defecto responde el motor local y no se realizó ninguna llamada externa.
> La activación en la web (Yajaira) y Android (Amore) queda en sus capas.

## `POST /api/asistente/interpretar`

Público: funciona sin sesión. Si llega `Authorization: Bearer <token>` válido,
la respuesta añade el resumen de datos propios. No requiere rol especial.

```json
{ "mensaje": "Perdí mi celular azul en la biblioteca" }
```

Reglas del mensaje: obligatorio, no vacío y máximo 300 caracteres. El campo
`mensaje` es el único que se acepta.

Respuesta `200`:

```json
{
  "intencion": "buscar",
  "tipoBuscado": "ENCONTRADO",
  "categoria": { "id": 1, "nombre": "Celular" },
  "ubicacion": "biblioteca",
  "palabras": ["celular", "azul"],
  "colores": ["azul"],
  "fuente": "local",
  "resumenPropio": "Tienes 2 publicaciones: 1 activa, 1 en custodia y 0 recuperadas. ...",
  "datosPropios": {
    "publicaciones": { "total": 2, "activas": 1, "enCustodia": 1, "recuperadas": 0 },
    "reclamos": { "total": 1, "pendientes": 1, "aceptados": 0, "retirados": 0, "rechazados": 0 },
    "qrRetiroVigentes": 0
  }
}
```

- `intencion`: `buscar`, `publicar`, `ayuda`, `saludo`, `gracias` o `vacio`.
- `tipoBuscado`: `ENCONTRADO` (si el usuario perdió algo), `PERDIDO` (si
  encontró algo) o `""`.
- `categoria.id` siempre pertenece al catálogo activo; puede ser `null`.
- `palabras` y `colores` van en singular, sin tildes y sin datos personales.
- `fuente`: `local` o `claude`. Indica qué motor produjo la interpretación.
- `datosPropios`, `resumenPropio`: solo con sesión. Son **cifras**, no nombres,
  correos, teléfonos ni tokens de QR. Sin sesión se omiten.

El contrato que ya consume `frontend/usuario/src/utils/asistente.js`
(`intencion`, `tipoBuscado`, `categoria.id`, `ubicacion`, `palabras`, `colores`)
no cambia; los campos nuevos son opcionales.

## Motor local

Siempre disponible. Porta las reglas de `asistente.js`: normalización sin
tildes, raíces aproximadas, sinónimos del campus, colores, lugares, verbos de
pérdida/hallazgo y categorías reales de la base. Se usa cuando:

- no hay proveedor de IA configurado,
- el proveedor responde error o tarda más de `timeout-ms`,
- la respuesta no pasa la validación.

## Proveedor de IA (opcional)

Variables de entorno (nunca en Git):

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `ASISTENTE_CLAUDE_ENABLED` | `false` | activa el proveedor |
| `ANTHROPIC_API_KEY` | vacío | clave; sin ella se usa el motor local |
| `ASISTENTE_CLAUDE_MODEL` | `claude-sonnet-4-5` | modelo configurable |
| `ASISTENTE_CLAUDE_URL` | `https://api.anthropic.com/v1/messages` | endpoint |
| `ASISTENTE_CLAUDE_TIMEOUT_MS` | `8000` | tiempo máximo |
| `ASISTENTE_LIMITE_POR_MINUTO` | `20` | límite por usuario o IP |

Privacidad: al proveedor solo se envían el mensaje redactado (correos y
números largos se sustituyen por `[dato personal]`) y los nombres/descripciones
de las categorías. **No se envían** publicaciones, reclamos, QR, correo,
teléfono ni nombre del usuario. El resumen propio se calcula en el servidor y
se devuelve al cliente, no viaja al proveedor.

Validación de la respuesta de IA: intención y tipo dentro de la lista; categoría
existente y activa; máximo 12 palabras de hasta 30 caracteres; ubicación de
hasta 80 caracteres; colores de la lista conocida. Si algo no cumple, se
descarta completa y responde el motor local.

## Límite de uso

Ventana fija de un minuto por usuario autenticado o por IP. Al superar
`ASISTENTE_LIMITE_POR_MINUTO` responde `429` con `Retry-After` en segundos.

## Errores

| Código | Caso |
| --- | --- |
| `400` | mensaje vacío, ausente, mayor de 300 caracteres o JSON inválido |
| `429` | límite de consultas por minuto |
| `500` | fallo interno inesperado |

El frontend ya trata cualquier error de este endpoint como “sin IA” y usa su
motor local, por lo que el asistente sigue funcionando.
