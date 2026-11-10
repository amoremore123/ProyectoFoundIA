# H43 — Bandeja de reclamos de custodia y QR de retiro

> Estado de esta entrega: API/datos implementados y probados en Django y Spring.
> Las pantallas web (Yajaira) y Android (Amore) quedan pendientes en sus capas.

## Permisos

Solo `BIBLIOTECA` y `ADMIN` activos. Un `USUARIO` recibe `403`. La bandeja solo
muestra reclamos de objetos que tienen una fila en `custodias`; los reclamos de
publicaciones que nunca entraron a la biblioteca no aparecen aquí.

## `GET /api/admin/reclamos/`

Lista paginada de 10 en 10, con la respuesta de verificación de cada reclamo.

| Parámetro | Ejemplo | Efecto |
| --- | --- | --- |
| `estado` | `PENDIENTE` / `ACEPTADO` / `RECHAZADO` / `RETIRADO` | filtra por estado |
| `q` | `Mochila` / `Ana` | busca por objeto o por reclamante |
| `objetoId` | `12` | reclamos de una publicación |
| `desde` | `2026-11-01` | fecha del reclamo desde |
| `hasta` | `2026-11-09` | fecha del reclamo hasta |

Respuesta:

```json
{
  "count": 1,
  "next": null,
  "previous": null,
  "results": [
    {
      "id": 8,
      "objeto": {
        "id": 12,
        "nombre": "Mochila azul",
        "descripcion": "Mochila con cierre rojo",
        "ubicacion": "Biblioteca central",
        "fecha_objeto": "2026-11-08",
        "tipo": "ENCONTRADO",
        "estado": "EN_CUSTODIA",
        "categoria": "Mochilas"
      },
      "reclamante": { "id": 21, "nombre": "Ana Pérez" },
      "estado": "PENDIENTE",
      "fechaReclamo": "2026-11-09T14:00:00Z",
      "resueltoPor": null,
      "fechaResolucion": null,
      "motivoResolucion": null,
      "qrRetiroExpira": null,
      "qrRetiroUsadoEn": null,
      "qrRetiroVigente": false,
      "qrRetiroUrl": null
    }
  ]
}
```

La respuesta de verificación incluye el estado del objeto en custodia y los
datos del QR de retiro (`qrRetiroExpira`, `qrRetiroUsadoEn`, `qrRetiroVigente`,
`qrRetiroUrl`). No expone correo ni teléfono del reclamante.

## `POST /api/admin/reclamos/{id}/resolver/`

```json
{
  "decision": "ACEPTADO",
  "motivo": "Documento de identidad y descripción verificados"
}
```

- `decision`: `ACEPTADO` o `RECHAZADO`; `motivo` obligatorio.
- El responsable y la fecha los registra el servidor desde el token.
- Solo se resuelve una vez: un segundo intento responde `409`.
- Aceptar cuando ya existe otro reclamo `ACEPTADO` o `RETIRADO` del mismo objeto
  responde `409` y no cambia nada.
- Aceptar rechaza automáticamente los reclamos rivales pendientes del mismo
  objeto, con el motivo «Se aceptó otro reclamo para este objeto.», y avisa a
  cada reclamante.
- Aceptar genera un token de retiro aleatorio de un solo uso, válido 7 días, y
  notifica al reclamante aceptado. Rechazar notifica el motivo y no genera QR.

Respuesta `200`: mismo formato de la bandeja con `estado` resuelto.

## `GET /api/admin/reclamos/{id}/qr-retiro/`

Devuelve `application/pdf` (A5) con el QR de retiro, el objeto, el reclamante y
la fecha de vencimiento. Responde `409` si el reclamo no fue aceptado, el QR ya
fue usado o venció.

## `GET /api/admin/reclamos/verificar-qr/?token=...`

Valida sin consumir. Respuesta:

```json
{
  "valido": true,
  "estadoQr": "VIGENTE",
  "reclamo": { "id": 8, "estado": "ACEPTADO", "...": "..." }
}
```

`estadoQr`: `VIGENTE`, `VENCIDO`, `USADO`, `SIN_QR` o `NO_VIGENTE`. Token
inexistente: `404`; token vacío: `400`.

## `POST /api/admin/reclamos/retirar/`

```json
{ "token": "<token del QR>" }
```

Consume el QR una sola vez y, en una sola transacción:

- reclamo: `RETIRADO` y `qr_retiro_usado_en`.
- custodia: `RETIRADO`.
- objeto: `RECUPERADO` y `fecha_recuperacion`.
- notificación «Retiro registrado» al reclamante.

Un segundo intento, un QR vencido o un objeto que ya no está en custodia
responden `409`. Token inexistente: `404`.

## Datos

- `reclamos`: `resuelto_por_id`, `fecha_resolucion`, `motivo_resolucion`,
  `qr_retiro_token` único, `qr_retiro_expira`, `qr_retiro_usado_en`; el estado
  admite `RETIRADO`.
- `notificaciones.enlace`: ruta del objeto para abrir el aviso.
- Migración: `database/migracion_h43_reclamos_custodia.sql`.

## Cambio en Spring

`POST /api/objetos/{id}/reclamos` ahora acepta objetos `EN_CUSTODIA`, además de
`ACTIVO`. Un objeto `RECUPERADO`, `OCULTO`, `ELIMINADO` o de tipo `PERDIDO`
sigue respondiendo `400`.
