# H46 — Hablar con el asistente por voz

Responsable: Amore (Android) y Yajaira (web). El reconocimiento de voz no usa una API
propia: lo hace el proveedor del navegador (Web Speech API, `es-PE`) o del teléfono
(`SpeechRecognizer`). El texto reconocido se revisa y se envía a
`POST /api/asistente/interpretar` de [H45](sprint3-h45.md).

Antes de usar el micrófono se pide el permiso y se avisa que el reconocimiento lo procesa
ese proveedor. Si se niega, se puede escribir.

## Lectura en voz alta

- Android: `TextToSpeech` del dispositivo. No llama al servidor.
- Web: `POST /api/asistente/voz` (voz natural opcional). Si responde error, la web lee con la voz del navegador.

### `POST /api/asistente/voz` (Spring)

Público (`permitAll`). Con sesión, el límite se cuenta por usuario; sin sesión, por IP.

```json
{ "texto": "Encontré 2 publicaciones que coinciden" }
```

- `texto`: obligatorio, máximo 600 caracteres.
- Respuesta `200` con `Content-Type: audio/mpeg` y `Cache-Control: private, max-age=3600`.

| Código | Caso |
| --- | --- |
| `200` | Audio MP3 |
| `400` | Texto vacío o de más de 600 caracteres |
| `429` | Límite por minuto o por día superado |
| `503` | Sin `VOZ_API_KEY` o `VOZ_ID` configurados, o el proveedor no responde |

Configuración (`application.yml`, solo variables de entorno, nunca en Git): `VOZ_URL`
(ElevenLabs por defecto), `VOZ_API_KEY`, `VOZ_ID`, `VOZ_MODELO`, `VOZ_TIMEOUT_MS` (10000),
`VOZ_LIMITE_POR_MINUTO` (10) y `VOZ_LIMITE_CARACTERES_DIA` (2000).

## Verificación

Ver [entrega de Amore](../integracion/entrega-amore-sprint3.md) (permiso de micrófono negado
conserva el campo de texto) y [entrega de Yajaira](../integracion/entrega-yajaira-sprint3.md).
La prueba con ruido en un teléfono físico queda en M2 del Sprint 4.
