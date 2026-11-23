# H68 — Elegir el canal de avisos

Responsable del backend: Leonel (Spring). Web: Yajaira (dos interruptores en las preferencias de
Notificaciones). Android: Amore (los mismos interruptores en Ajustes). Backlog 5.5. Prioridad «Si sobra».

Hasta ahora cada persona elegía **qué** avisos recibir (H24 y H53: coincidencias, contactos, reclamos,
recordatorios). H68 agrega **por dónde**: push en el teléfono (H57) y correo electrónico.

## Cómo se combinan

| Canal | Cuándo llega |
| --- | --- |
| Bandeja de Notificaciones (web y app) | Siempre que el tipo de aviso esté activo. **No se puede apagar**: es el registro de todos los avisos |
| Push | Tipo activo **y** `push: true` **y** la cuenta tiene un teléfono registrado (H57) |
| Correo | Tipo activo **y** `correo: true` **y** la cuenta está `ACTIVO` y con el correo verificado |

Por defecto: `push: true` (como en H57) y `correo: false` (nadie empieza a recibir correos sin pedirlo).

## Ruta (Spring, la misma de H24)

`GET` y `PUT /api/notificaciones/preferencias`, con sesión.

```json
{
  "coincidencias": true,
  "contactos": true,
  "reclamos": true,
  "recordatorios": true,
  "push": true,
  "correo": false
}
```

| Campo | Regla |
| --- | --- |
| `coincidencias`, `contactos`, `reclamos` | Obligatorios, como en H24 |
| `recordatorios` | Opcional (H53): si no llega, se conserva el valor guardado |
| `push`, `correo` | **Opcionales**: si no llegan, se conserva el valor guardado. Así la web y la app actuales siguen funcionando sin cambios |

Errores: `400` si falta un obligatorio o un valor no es booleano, `401` sin sesión.

## Correo de aviso

- Un proceso de Spring revisa cada minuto los avisos nuevos (de Spring y de Django) y envía el correo a los
  que corresponde. Solo avisos de los últimos 30 minutos: al activar el correo no llega el historial.
- Cada aviso se marca antes de enviarlo (`correo_enviado_en`), así que aunque corran dos instancias se envía
  una sola vez. El resultado queda en `correo_resultado`: `ENVIADO`, `OMITIDO` (el canal o el tipo están
  apagados, la cuenta no puede recibir correos o es un mensaje agrupado) o `ERROR`.
- **Chat:** como máximo **un correo por conversación cada 30 minutos**. Los mensajes siguientes siguen en la
  bandeja y por push.
- El correo lleva el título y el texto del aviso (los mismos de la bandeja, sin el texto de los mensajes del
  chat ni datos de contacto) y un enlace a la web: la conversación (`/mensajes/{id}`) o Notificaciones.
  Al pie explica cómo dejar de recibirlos.
- Si el servidor de correo falla, el aviso queda en `ERROR` y no se reintenta (sigue en la bandeja).

Configuración (`application.yml`, con valores por defecto):

| Variable | Por defecto |
| --- | --- |
| `CORREO_AVISOS_INTERVALO_MS` | `60000` |
| `CORREO_AVISOS_VENTANA_MINUTOS` | `30` |
| `CORREO_AVISOS_CHAT_MINUTOS` | `30` |

## Base de datos

`database/migracion_h68_canal_avisos.sql` (idempotente, también en `script.sql`):

```sql
ALTER TABLE preferencias_notificacion ADD COLUMN canal_push BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE preferencias_notificacion ADD COLUMN canal_correo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notificaciones ADD COLUMN correo_enviado_en DATETIME NULL;
ALTER TABLE notificaciones ADD COLUMN correo_resultado VARCHAR(10) NULL;
CREATE INDEX idx_notificaciones_correo ON notificaciones (correo_enviado_en, fecha_creacion);
```

## Lo que necesitan la web y Android

- Leer `push` y `correo` del `GET` y mostrar dos interruptores: «Avisos en el teléfono» y «Avisos por
  correo», con el correo de la cuenta al lado.
- Enviarlos en el `PUT` junto con los demás campos.
- Explicar que la bandeja de Notificaciones siempre guarda los avisos.

## Pruebas que se deben ejecutar y registrar

- Preferencias: valores por defecto; guardar y leer los dos canales; un `PUT` sin `push` ni `correo` (cliente
  de H24/H53) conserva lo guardado; valor no booleano `400`; sin sesión `401`.
- Push: con `push: false` no se envía aunque haya teléfono; el aviso sigue en la bandeja.
- Correo: con `correo: true` llega un correo por aviso; con `correo: false`, con el tipo apagado, con la cuenta
  suspendida o sin verificar, no (`OMITIDO`); aviso viejo, no; un aviso nunca se envía dos veces; chat agrupado
  por conversación cada 30 minutos; fallo del servidor de correo `ERROR`; avisos creados por Django también.
- Con MySQL real y Mailpit: la migración dos veces y el correo recibido en la bandeja de Mailpit.
