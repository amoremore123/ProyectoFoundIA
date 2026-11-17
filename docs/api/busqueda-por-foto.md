# Buscar con una foto en FINDI

> Mejora del asistente (H45/H47), 14/11/2026. Web: botón de cámara en el chat de FINDI.

## `POST /api/asistente/buscar-por-foto`

Público, como `POST /api/asistente/interpretar`. Con sesión, el límite se cuenta por usuario;
sin sesión, por IP.

```json
{ "imagen": "data:image/jpeg;base64,/9j/4AAQ..." }
```

- `imagen`: JPEG o PNG como data URL, hasta **2 MiB** ya decodificada. El servidor comprueba la
  firma del archivo (no basta con el prefijo). La web la reduce antes a máx. 1024 px.

Respuesta `200`:

```json
{
  "descripcion": "Lentes de sol con marco de carey",
  "categoria": { "id": 6, "nombre": "Accesorio" },
  "palabras": ["lentes", "sol", "marco", "carey"],
  "colores": ["marrón", "negro"]
}
```

- `categoria`: categoría **activa** del catálogo o `null`; el nombre sale de la base.
- `descripcion`: hasta 120 caracteres o `""`. Se vacía si trae enlaces, correos o números largos.
- `palabras` y `colores`: hasta 6, en minúsculas; se descartan enlaces y números de 4+ cifras.
- Si la foto no muestra un objeto reconocible, `descripcion` vacía y listas vacías.

La web usa estos datos para buscar entre las publicaciones reales (mismo motor que el texto) y
ofrece «Publicar con esta foto», que abre Publicar con la foto ya adjunta.

Errores: `400` no es JPEG/PNG real o supera 2 MiB; `429` límite de uso; `503` IA sin configurar,
caída, con límite del proveedor o con respuesta fuera de contrato.

## Privacidad

- La foto **no se guarda, no se registra y no se publica**: solo se envía al proveedor de IA.
- Antes de la primera foto, la web avisa que va al proveedor y que no conviene enviar
  documentos, rostros u otros datos personales.
- Las instrucciones al modelo prohíben transcribir textos, nombres, números o describir rostros,
  y seguir instrucciones escritas en la imagen.

## Configuración

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `IA_API_KEY` | — | Clave de Groq (en `.env`, nunca en Git) |
| `IA_MODELO_VISION` | `qwen/qwen3.8-27b` | Modelo de Groq que acepta imágenes (los gpt-oss solo texto) |

## Pruebas

- Spring: `BusquedaPorFotoServiceTest` (8): descripción válida, categoría inactiva, datos
  personales, imagen falsa o de otro formato, más de 2 MiB, IA no disponible, límite, instrucciones.
- Web: `ChatFoto.test.jsx` (4): aviso, búsqueda y «Publicar con esta foto», IA caída, foto sin objeto.
- Real (14/11/2026): foto del celular de unos lentes → «Lentes de sol con marco de carey»,
  Accesorio, en 1,4 s. Con tres fotos seguidas el plan gratuito de Groq respondió 429 (se ofrece
  describir con palabras o publicar con la foto).
