# H57 — Dispositivos y aviso push con Firebase

Responsable del backend: Leonel. Android: Amore (servicio de mensajes, canal y apertura del aviso).
Web: Yajaira (aviso «Mensaje nuevo» en Notificaciones). Backlog 5.3; semanas 12 y 13 del curso.

El push **no reemplaza** la bandeja de notificaciones: cada aviso se sigue guardando en
`notificaciones` como hoy, y el push es solo un empujón al teléfono para avisar que hay algo nuevo.

## Rutas (Spring)

Todas requieren sesión (JWT) y usan la cuenta del token, nunca un id enviado por el cliente.

### `POST /api/dispositivos`

Registra o renueva el token de Firebase Cloud Messaging (FCM) del teléfono.

```json
{ "token": "<token de FCM>", "plataforma": "ANDROID" }
```

| Campo | Regla |
| --- | --- |
| `token` | Obligatorio, de 20 a 512 caracteres, solo `A-Z a-z 0-9 : _ -` |
| `plataforma` | Obligatorio: `ANDROID` (se reserva `WEB` para más adelante; hoy responde `400`) |

Respuesta:

```json
{ "id": 12, "plataforma": "ANDROID", "fechaRegistro": "2026-11-20T15:20:00", "fechaUltimoUso": "2026-11-20T15:20:00" }
```

- **Idempotente:** registrar dos veces el mismo token en la misma cuenta devuelve el mismo `id`
  y solo actualiza `fechaUltimoUso` (`200`). No crea otra fila.
- **Un token pertenece a una sola cuenta:** si el teléfono cambia de cuenta, el mismo token pasa
  a la cuenta nueva y deja de recibir los avisos de la anterior (`200`, mismo comportamiento).
  La respuesta nunca revela que el token era de otra cuenta.
- Máximo **10 dispositivos por cuenta**: al registrar el undécimo se elimina el de uso más antiguo.
- Se guarda el `jti` de la sesión que lo registró, para borrarlo al cerrar esa sesión (ver abajo).

| Código | Caso |
| --- | --- |
| `201` | Token nuevo registrado |
| `200` | Token ya registrado (renovado o pasado a esta cuenta) |
| `400` | Token vacío, largo o con caracteres inválidos; `plataforma` distinta de `ANDROID` |
| `401` | Sin token de sesión, vencido o revocado |

### `DELETE /api/dispositivos/{id}`

Da de baja un dispositivo propio (por ejemplo, al desactivar los avisos en el teléfono).

| Código | Caso |
| --- | --- |
| `204` | Eliminado |
| `401` | Sin sesión |
| `404` | No existe **o es de otra cuenta** (no se revela cuál de los dos) |

### `GET /api/dispositivos`

Lista los dispositivos propios (`id`, `plataforma`, `fechaRegistro`, `fechaUltimoUso`).
**Nunca devuelve el token de FCM.**

## Cierre de sesión (H37)

- `POST /api/auth/logout` y `POST /api/admin/auth/logout/`: borran los dispositivos registrados
  con el `jti` de esa sesión.
- `POST /api/auth/logout-all` y `POST /api/admin/auth/logout-all/`: borran **todos** los
  dispositivos de la cuenta.
- Suspender una cuenta también borra sus dispositivos.

Android, además, debe llamar a `DELETE /api/dispositivos/{id}` antes de cerrar sesión y borrar
su token local; el borrado del servidor cubre el caso en que el teléfono no alcance a hacerlo.

## Envío del push

Un **despachador** en Spring revisa cada 15 s los avisos nuevos de `notificaciones` que aún no
tienen push enviado y los manda por FCM a los dispositivos de su destinatario. Así cubre igual
los avisos que crea Spring y los que crea Django (reclamos resueltos, H43), con una sola
integración con Firebase.

| Tipo de aviso | Origen | Preferencia de H24 que se respeta |
| --- | --- | --- |
| `COINCIDENCIA_NUEVA` | Spring | `coincidencias` |
| `CONTACTO_RECIBIDO` | Spring | `contactos` |
| `RECLAMO_RECIBIDO` | Spring | `reclamos` |
| `RECLAMO` | Django | `reclamos` |
| `RECORDATORIO_OBJETO` | Spring | `recordatorios` |
| `MENSAJE_CHAT` | Spring (H55) | se agregará con el chat |

- Con la preferencia apagada **no se envía push** (el aviso puede seguir en la bandeja si su
  historia ya lo guardaba así).
- Solo se envían avisos creados en los **últimos 30 minutos**: al encender el push por primera
  vez no llega el historial viejo.
- Cada aviso se envía **una sola vez**, aunque el despachador corra en dos instancias
  (se marca con bloqueo de fila antes de enviar).
- Si FCM responde que el token ya no existe (`UNREGISTERED` o `INVALID_ARGUMENT` del token),
  ese dispositivo se borra.

Mensaje enviado (FCM HTTP v1):

```json
{
  "message": {
    "token": "<token>",
    "notification": { "title": "Tu reclamo fue aceptado", "body": "Acércate a la biblioteca..." },
    "data": { "notificacionId": "31", "tipo": "RECLAMO", "enlace": "/objeto/3" },
    "android": { "notification": { "channel_id": "avisos" } }
  }
}
```

- El texto es el mismo `titulo` y `mensaje` del aviso: nunca incluye correo, teléfono ni datos de otra persona.
- `enlace` es la ruta que Android abre al tocar el aviso (la misma que usa la web).
- Canal de Android: `avisos`. Para el chat (`MENSAJE_CHAT`) se usará el canal `mensajes`.

## Sin cuenta de servicio de Firebase

La cuenta de servicio va **solo en variables de entorno**, nunca en Git:

| Variable | Uso |
| --- | --- |
| `FIREBASE_CREDENCIALES_JSON` | Contenido del JSON de la cuenta de servicio (o vacío) |
| `FIREBASE_PROYECTO_ID` | Id del proyecto de Firebase |
| `PUSH_INTERVALO_MS` | Intervalo del despachador (por defecto `15000`) |

Sin `FIREBASE_CREDENCIALES_JSON` el servidor arranca igual, registra **una vez** en el log que el
push está apagado, los avisos se guardan como siempre y las rutas de dispositivos funcionan.
**Nunca responde `500` por falta de Firebase.**

## Base de datos

`database/migracion_h57_dispositivos.sql` (idempotente, con respaldo previo; también en `script.sql`):

```sql
CREATE TABLE dispositivos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  token VARCHAR(512) NOT NULL,
  plataforma VARCHAR(10) NOT NULL,
  sesion_jti VARCHAR(64) NULL,
  fecha_registro DATETIME NOT NULL,
  fecha_ultimo_uso DATETIME NOT NULL,
  UNIQUE KEY uk_dispositivo_token (token),
  CONSTRAINT fk_dispositivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
ALTER TABLE notificaciones ADD COLUMN push_enviado_en DATETIME NULL;
```

## Pruebas que se deben ejecutar y registrar

- Registrar el mismo token dos veces no lo duplica; el mismo token en otra cuenta pasa a esa cuenta.
- Una cuenta no puede borrar el dispositivo de otra (`404`) ni verlo en su lista.
- `GET /api/dispositivos` no devuelve tokens.
- Cerrar sesión borra los dispositivos de esa sesión; cerrar en todos los dispositivos borra todos.
- Preferencia apagada: no se envía push. Aviso de otra cuenta: no llega a mis dispositivos.
- Sin cuenta de servicio: el aviso se crea, las rutas responden y no hay `500`.
- Un aviso se envía una sola vez; un token rechazado por FCM se borra.
- Con la cuenta de servicio del equipo: push real con la app cerrada (Android, M2).

## Lo que necesita de Amore

- El proyecto de Firebase y su cuenta de servicio (fuera de Git) para la prueba real.
- En Android: `google-services.json` fuera de Git, registrar y renovar el token con
  `POST /api/dispositivos`, crear los canales `avisos` y `mensajes`, abrir `data.enlace` al tocar
  el aviso y borrar el dispositivo al cerrar sesión.
