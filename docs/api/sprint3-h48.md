# H48 — Política de privacidad y términos públicos

> Estado: API (Spring, desde H33) y web implementadas. La capa Android (Amore) enlaza a las
> mismas páginas o consume este contrato. No se creó un endpoint nuevo: H48.1 reutiliza el de H33.

## `GET /api/legal/documentos`

**Público**: no requiere `Authorization` (regla `permitAll` en `SecurityConfig`). Lo usan el
registro (H33) y las páginas públicas `/privacidad` y `/terminos` de la web (H48).

Respuesta `200`:

```json
{
  "version": "foundia-tecsup-2026-11-05-v2",
  "estado": "FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA",
  "terminos": "FoundIA es un proyecto académico… \n\n# Términos de uso de FoundIA\n## …",
  "privacidad": "FoundIA es un proyecto académico… \n\n# Política de privacidad de FoundIA\n## …",
  "sha256": "<huella SHA-256 de términos + privacidad>",
  "aviso": "FoundIA es un proyecto académico. Las fuentes oficiales de Tecsup son referencias institucionales…",
  "fuentes": [
    { "id": "terminos-tecsup", "titulo": "…", "url": "https://www.tecsup.edu.pe/…",
      "consultadoEl": "2026-11-05", "sha256Original": "…", "sha256Texto": "…" },
    { "id": "privacidad-tecsup", "titulo": "…", "url": "https://www.tecsup.edu.pe/…",
      "consultadoEl": "2026-11-05", "sha256Original": "…", "sha256Texto": "…" }
  ]
}
```

- `version`: identifica la versión vigente; **incluye su fecha** (`2026-11-05`). Es la misma que
  el registro envía en `versionLegal` y que se guarda con la aceptación (H33).
- `terminos` y `privacidad`: Markdown simple (`#` título, `##` subtítulo, párrafos). La web los
  muestra con `TextoLegal`; en la página pública el `#` es un `h2` porque va después del `h1` (H51).
- `sha256`: huella de ambos textos; si cambia el texto, cambia la huella y la versión.
- `fuentes`: referencias oficiales de Tecsup con su fecha de consulta y huellas. El servidor
  rechaza arrancar si una fuente no coincide con su huella o no es de `www.tecsup.edu.pe`.

Errores: no tiene errores de cliente (no recibe parámetros). Si los documentos no se pueden
cargar, la aplicación no arranca; la web muestra «No se pudo cargar el documento» con «Reintentar».

## Relación con el registro (H33)

`POST /api/auth/register` exige `aceptaTerminos: true` y `versionLegal` igual a `version`.
Si la versión cambió, responde `400` con «La versión de los documentos cambió. Léelos de nuevo
antes de registrarte.»

## Web

| Ruta | Sesión | Contenido |
| --- | --- | --- |
| `/privacidad` | No | `privacidad`, con enlaces a términos y a cookies |
| `/terminos` | No | `terminos`, con enlaces a privacidad y a cookies |
| `/cookies` | No | Qué guarda la web en el navegador y la elección del aviso de cookies (sin API) |

Enlaces: pie de la portada, registro e inicio de sesión, asistente FINDI y panel administrativo
(`VITE_WEB_URL`). Texto del proyecto, sujeto a revisión institucional.
