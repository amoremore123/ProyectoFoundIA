# H68 — Elegir el canal de avisos

Responsable del backend: Leonel (Spring). Web: Yajaira (el canal de cada tipo de aviso en las preferencias de
Notificaciones). Android: Amore (lo mismo en Ajustes). Backlog 5.5. Prioridad «Si sobra».

Hasta ahora cada persona elegía **qué** avisos recibir (H24 y H53: coincidencias, contactos, reclamos,
recordatorios). H68 agrega **por dónde llega cada tipo**, como pide el Sprint 4 («preferencia de canal por
tipo de aviso, push o correo»): por ejemplo, los mensajes del chat por push y las coincidencias por correo.

> **Corrección del 22/11/2026.** La primera versión de H68 (commits `11d79cf` y `1f04350`) tenía una sola
> elección general (`push` y `correo` verdadero o falso) en lugar de una por tipo. No cumplía el Sprint 4 y se
> reemplazó por la de este documento. La migración pasa lo que se haya guardado con la versión anterior (ver
> «Base de datos»). Ningún cliente usaba todavía los campos `push` y `correo`.

## Canales

| Canal | Push en el teléfono (H57) | Correo |
| --- | --- | --- |
| `PUSH` (por defecto) | Sí | No |
| `CORREO` («solo correo») | **No** | Sí |
| `AMBOS` | Sí | Sí |
| `NINGUNO` («solo la bandeja») | No | No |

- La **bandeja de Notificaciones** (web y app) recibe el aviso siempre que su tipo esté activo; el canal solo
  decide si además sale por push o por correo. Apagar un tipo (H24/H53) sigue apagándolo del todo.
- El push necesita un teléfono registrado (H57). El correo, que la cuenta esté `ACTIVO` y verificada.
- Por defecto todo es `PUSH`: se comporta como antes de H68 y nadie recibe correos sin pedirlo.

| Clave de `canales` | Tipos de aviso |
| --- | --- |
| `coincidencias` | `COINCIDENCIA_NUEVA` |
| `contactos` | `CONTACTO_RECIBIDO` |
| `reclamos` | `RECLAMO_RECIBIDO` y `RECLAMO` (de Django) |
| `recordatorios` | `RECORDATORIO_OBJETO` |
| `mensajes` | `MENSAJE_CHAT` |
| `sistema` | `SISTEMA` (avisos de la biblioteca y del panel, de Django) |

## Ruta (Spring, la misma de H24)

`GET` y `PUT /api/notificaciones/preferencias`, con sesión.

```json
{
  "coincidencias": true,
  "contactos": true,
  "reclamos": true,
  "recordatorios": true,
  "canales": {
    "coincidencias": "CORREO",
    "contactos": "AMBOS",
    "reclamos": "PUSH",
    "recordatorios": "NINGUNO",
    "mensajes": "PUSH",
    "sistema": "PUSH"
  }
}
```

| Campo | Regla |
| --- | --- |
| `coincidencias`, `contactos`, `reclamos` | Obligatorios, como en H24 |
| `recordatorios` | Opcional (H53): si no llega, se conserva el valor guardado |
| `canales` | **Opcional**, y cada clave también: lo que no llega conserva lo guardado. Así la web y la app actuales siguen funcionando sin cambios. El `GET` siempre devuelve las 6 claves |

Errores: `400` si falta un obligatorio, un valor no es booleano, un canal no es `PUSH`, `CORREO`, `AMBOS` o
`NINGUNO`, o `canales` trae una clave que no existe; `401` sin sesión.

## Correo de aviso

- Un proceso de Spring revisa cada minuto los avisos nuevos (de Spring y de Django) y envía el correo a los
  que corresponde. Solo avisos de los últimos 30 minutos: al activar el correo no llega el historial.
- Cada aviso se marca antes de enviarlo (`correo_enviado_en`), así que aunque corran dos instancias se envía
  una sola vez. El resultado queda en `correo_resultado`: `ENVIADO`, `OMITIDO` (el canal de ese tipo no incluye
  correo, el tipo está apagado, la cuenta no puede recibir correos o es un mensaje agrupado) o `ERROR`.
- **Chat:** como máximo **un correo por conversación cada 30 minutos**. Los mensajes siguientes siguen en la
  bandeja y, si el canal lo incluye, por push.
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
ALTER TABLE preferencias_notificacion
  ADD COLUMN canal_coincidencias VARCHAR(8) NOT NULL DEFAULT 'PUSH',  -- y lo mismo para
  -- canal_contactos, canal_reclamos, canal_recordatorios, canal_mensajes y canal_sistema,
  -- con CHECK (... IN ('PUSH','CORREO','AMBOS','NINGUNO'))
ALTER TABLE notificaciones ADD COLUMN correo_enviado_en DATETIME NULL;
ALTER TABLE notificaciones ADD COLUMN correo_resultado VARCHAR(10) NULL;
CREATE INDEX idx_notificaciones_correo ON notificaciones (correo_enviado_en, fecha_creacion);
```

Si la base tiene las columnas de la primera versión (`canal_push`, `canal_correo`), la migración copia lo
guardado a los 6 canales (push y correo → `AMBOS`; solo push → `PUSH`; solo correo → `CORREO`; ninguno →
`NINGUNO`) y después quita esas dos columnas. Se puede ejecutar más de una vez.

## Lo que necesitan la web y Android

- Leer `canales` del `GET` y mostrar, junto a cada tipo de aviso, un selector: «Teléfono», «Correo», «Teléfono
  y correo» o «Solo en la bandeja». Mostrar el correo de la cuenta.
- Enviar `canales` en el `PUT` junto con los demás campos.
- Explicar que la bandeja de Notificaciones siempre guarda los avisos de los tipos activos.

## Pruebas que se deben ejecutar y registrar

- Preferencias: valores por defecto; guardar y leer los canales; enviar solo una clave conserva las demás; un
  `PUT` sin `canales` (cliente de H24/H53) conserva lo guardado; canal inválido, clave desconocida o valor no
  booleano `400`; sin sesión `401`.
- Push: con el tipo en «solo correo» (`CORREO`) o `NINGUNO` no se envía push aunque haya teléfono, y el aviso
  sigue en la bandeja; con `PUSH` o `AMBOS`, sí. El canal de un tipo no afecta a los demás.
- Correo: con `CORREO` o `AMBOS` llega un correo; con `PUSH`, `NINGUNO`, el tipo apagado, la cuenta suspendida o
  sin verificar, no (`OMITIDO`); aviso viejo, no; un aviso nunca se envía dos veces; chat agrupado por
  conversación cada 30 minutos; fallo del servidor de correo `ERROR`; avisos creados por Django también.
- Con MySQL real y Mailpit: la migración dos veces, también sobre una base con la primera versión, y el correo
  recibido en la bandeja de Mailpit.

## Primera versión (reemplazada)

La verificación de la primera versión (22/11/2026: 574 pruebas de Spring y la prueba con MySQL y Mailpit)
comprobó el despachador de correos, el agrupado del chat, el enlace y la codificación, que no cambian. La
elección general de canal se reemplazó por la de este documento.

Encontrado al probarla, fuera de H68: un método HTTP no permitido respondía `500` en vez de `405`. **Corregido**
en un commit aparte (`dc7f247`): ahora responde `405` con `Allow`, y un tipo de contenido no admitido, `415`.

## Verificación del 23/11/2026 (canal por tipo)

- Spring: `CanalAvisosIntegrationTest` rehecha con 11 pruebas (por defecto todo `PUSH`; guardar y leer los canales;
  enviar una sola clave conserva las demás; cliente de H24/H53 sin `canales` conserva lo guardado; canal inválido,
  en minúsculas, clave desconocida, valor no texto y booleano mal escrito `400`; sin sesión `401`; **con el tipo en
  `CORREO` o `NINGUNO` no se manda push** y el aviso queda en la bandeja, el canal de un tipo no afecta a los demás
  y con `AMBOS` vuelve el push; correo con `CORREO` y `AMBOS` una sola vez; omitido con `PUSH`, `NINGUNO`, tipo
  apagado o desconocido; cuenta suspendida o sin verificar; aviso viejo; chat agrupado; fallo `ERROR`; avisos de
  Django según su tipo). Suite completa: **581 pruebas, 0 fallos**.
- Una clave desconocida en `canales` (por ejemplo `chat`) se rechaza a propósito: la API en general ignora los
  campos que no conoce, y aquí eso dejaría un canal sin cambiar sin avisar.
- MySQL 8.0 de QA, con la base que ya tenía la **primera versión** aplicada:

| Caso | Resultado |
| --- | --- |
| Migración sobre la primera versión | Dos veces sin error. Push y correo → `AMBOS` (Ana), solo correo → `CORREO` (admin), ninguno → `NINGUNO` (Luis); `canal_push` y `canal_correo` quitadas; 6 restricciones `CHECK` creadas |
| Base | Un `UPDATE` con `canal_mensajes = 'SIEMPRE'` lo rechaza MySQL (`ERROR 3819`) |
| `GET` de Ana | Las 6 claves en `AMBOS`, como tenía guardado |
| `PUT` con 4 claves | `200`; las otras 2 se conservaron. Un `PUT` sin `canales` conservó todo |
| Canal inválido / clave desconocida | `400` / `400` |
| Correo (Mailpit) con coincidencias `CORREO`, contactos `AMBOS`, mensajes `PUSH`, sistema `NINGUNO` | Llegaron **2** correos (coincidencia y contacto); el mensaje y el aviso de sistema quedaron `OMITIDO`; Luis (`NINGUNO`), `OMITIDO` |
| Instalación nueva | `script.sql` y `seed.sql` en un MySQL vacío sin errores (también `script.sql` dos veces); las migraciones de H68, H70 y H71 sobre esa base, sin errores |

- El push real sigue sin probarse en QA (no tiene la cuenta de Firebase); la regla «solo correo no manda push» la
  cubren las pruebas con Firebase simulado, sobre el mismo despachador de H57.
- **Falta en la web (Yajaira) y en Android (Amore):** el selector de canal junto a cada tipo de aviso (ver «Lo que
  necesitan la web y Android»).
