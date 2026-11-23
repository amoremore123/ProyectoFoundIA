# H65 — Envíos sin conexión que no se duplican

Responsable del backend: Leonel (Spring). Android: Amore (cola de salida en Room, `mensajes_pendientes`).
Prioridad «Debería» del Sprint 4.

## El problema

Android guarda los mensajes de texto que se escriben sin red en una cola (`mensajes_pendientes` en Room) y
`ChatViewModels.enviarPendientes()` los reenvía en orden al volver la conexión. Si un envío llega
al servidor pero la respuesta se pierde (señal débil, la app se cierra), la app cree que falló y lo reenvía:
sin H65 el servidor lo guardaría **dos veces**.

## La solución: `Idempotency-Key`

El cliente genera un identificador único **cuando guarda el envío en la cola** (un UUID) y lo manda en el
encabezado `Idempotency-Key` en **todos** los reintentos de ese mismo envío. Si el servidor ya lo recibió,
devuelve el que guardó la primera vez en lugar de crear otro.

```http
POST /api/conversaciones/7/mensajes
Authorization: Bearer <jwt>
Idempotency-Key: 3f2a9c1e-5b7d-4e2a-9c1e-5b7d4e2a9c1e
Content-Type: application/json

{ "texto": "Hola, ¿sigue en la biblioteca?" }
```

| Ruta | Con `Idempotency-Key` repetida |
| --- | --- |
| `POST /api/conversaciones/{id}/mensajes` (texto, H55) | Devuelve el mismo mensaje |
| `POST /api/conversaciones/{id}/mensajes` (foto, H62) | Devuelve el mismo mensaje; la foto nueva no se guarda (hoy la app no pone fotos en la cola) |
| `POST /api/objetos` (publicar) | Devuelve la misma publicación |

Reglas:

- **Opcional:** sin el encabezado todo funciona como antes (la web no necesita cambiar).
- Formato: de 8 a 64 caracteres, solo letras, números, `-` y `_` (un UUID cumple). Otro formato: `400`.
- La clave es **por cuenta**: la misma clave de dos personas distintas son envíos distintos.
- Primera vez: `201`. Repetido: **`200`** con el mismo recurso (mismo `id`).
- Un repetido **no** cuenta para el límite de 20 mensajes por minuto, **no** vuelve a avisar al otro
  participante y se devuelve aunque la conversación ya haya pasado a solo lectura.
- La misma clave usada para un envío **distinto** (otra conversación, o un mensaje en una conversación y
  una publicación con la misma clave en otra ruta) se trata por separado en cada ruta; dentro del chat,
  reutilizarla en otra conversación responde `409`.
- No se compara el contenido: la clave identifica el envío. El cliente nunca debe reutilizar una clave
  para un envío nuevo.

| Código | Caso |
| --- | --- |
| `201` | Creado |
| `200` | Ya existía con esa clave: se devuelve el mismo |
| `400` | `Idempotency-Key` con formato inválido |
| `409` | En el chat, la clave ya se usó en otra conversación |

## Base de datos

`database/migracion_h65_idempotencia.sql` (idempotente, con respaldo previo; también en `script.sql`):

```sql
ALTER TABLE mensajes_chat ADD COLUMN id_cliente VARCHAR(64) NULL;
ALTER TABLE mensajes_chat ADD CONSTRAINT uq_mensajes_chat_cliente UNIQUE (autor_id, id_cliente);
ALTER TABLE objetos ADD COLUMN id_cliente VARCHAR(64) NULL;
ALTER TABLE objetos ADD CONSTRAINT uq_objetos_cliente UNIQUE (usuario_id, id_cliente);
```

La restricción única protege también el caso de dos reintentos que llegan a la vez.

## Lo que necesita Android (Amore)

Revisado contra `origin/mobile/android` (8ed7330, 21/11/2026): la cola solo guarda texto y la base Room está
en la versión 3.

- Agregar a `MensajePendienteEntity` un campo `idCliente` (UUID) generado **al encolar**, y una migración de
  Room 3 → 4 que agregue la columna y les dé un valor único a las filas ya guardadas.
- Enviar `@Header("Idempotency-Key")` en `enviarMensaje` y pasar `pendiente.idCliente` desde
  `enviarPendientes()` en cada reintento.
- `200` y `201` son éxito para Retrofit y traen el mismo `MensajeChat`: no hace falta tratarlos distinto.
- Si más adelante se ponen fotos o publicaciones en la cola, lo mismo en `enviarMensajeConFoto` y en
  `POST /api/objetos`.

## Pruebas que se deben ejecutar y registrar

- Enviar dos veces el mismo mensaje con la misma clave: `201` y `200`, el mismo `id`, un solo mensaje y un
  solo aviso para el otro participante.
- Sin clave: dos envíos iguales crean dos mensajes (comportamiento anterior).
- La misma clave de otra cuenta crea su propio mensaje.
- Reintento después de llegar al límite por minuto o de pasar a solo lectura: devuelve el original.
- Clave en otra conversación: `409`. Formato inválido: `400`.
- Publicar dos veces con la misma clave: una sola publicación.
- Con MySQL real: la migración se aplica dos veces y la restricción única rechaza un duplicado directo.


## Verificación del 22/11/2026

- Spring: 5 pruebas nuevas en `ChatIntegrationTest`: reintento con la misma clave (`201` y luego `200`, mismo
  `id`, un solo mensaje y un solo aviso, aunque el reintento traiga otro texto); sin clave se comporta como
  antes; la clave es por cuenta; el reintento se devuelve aunque la conversación ya esté en solo lectura
  (y un envío nuevo ahí sigue dando `409`); clave en otra conversación `409`; formato inválido `400`;
  publicar dos veces con la misma clave crea una sola publicación. Se ajustó `ObjetoControllerTest` a la
  nueva firma. Suite completa: **539 pruebas, 0 fallos**.
- MySQL 8.0 de QA: `migracion_h65_idempotencia.sql` se ejecutó dos veces sin error. Un `INSERT` directo
  con la misma clave y autor fue rechazado por la base (`ERROR 1062 … uq_mensajes_chat_cliente`).
- Por la API real: un mensaje enviado dos veces con la misma clave respondió `201` y `200` con el mismo `id`
  y quedó guardado **una vez**; una publicación igual (`201`, `200`, una sola fila); sin clave, `201` como
  antes; clave inválida, `400`.
- **Falta en Android (Amore):** hoy la cola de salida no envía `Idempotency-Key` (ver «Lo que necesita
  Android»). Hasta entonces el servidor ya está listo, pero un reintento de la app todavía puede duplicar.
