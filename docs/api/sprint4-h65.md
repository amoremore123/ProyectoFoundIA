# H65 — Envíos sin conexión que no se duplican

Responsable del backend: Leonel (Spring). Android: Amore (cola de salida en Room con WorkManager).
Prioridad «Debería» del Sprint 4.

## El problema

Android guarda lo que se escribe sin red en una cola y lo reenvía al volver la conexión. Si un envío llega
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
| `POST /api/conversaciones/{id}/mensajes` (foto, H62) | Devuelve el mismo mensaje; la foto nueva no se guarda |
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

- Agregar a `MensajePendienteEntity` un campo `idCliente` (UUID) generado **al encolar**, y una migración de
  Room para las filas ya guardadas (generarles uno).
- Enviar `@Header("Idempotency-Key") idCliente` en `enviarMensaje` y `enviarMensajeConFoto` en cada reintento.
- Tratar `200` igual que `201` (es el mismo mensaje).
- Si más adelante se agrega la cola de publicaciones, lo mismo en `POST /api/objetos`.

## Pruebas que se deben ejecutar y registrar

- Enviar dos veces el mismo mensaje con la misma clave: `201` y `200`, el mismo `id`, un solo mensaje y un
  solo aviso para el otro participante.
- Sin clave: dos envíos iguales crean dos mensajes (comportamiento anterior).
- La misma clave de otra cuenta crea su propio mensaje.
- Reintento después de llegar al límite por minuto o de pasar a solo lectura: devuelve el original.
- Clave en otra conversación: `409`. Formato inválido: `400`.
- Publicar dos veces con la misma clave: una sola publicación.
- Con MySQL real: la migración se aplica dos veces y la restricción única rechaza un duplicado directo.

