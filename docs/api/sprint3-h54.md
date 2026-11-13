# H54 — Gestionar los objetos no reclamados (disposición final)

> Estado: API (Django), migración y panel implementados. Pruebas de Django escritas
> (`nucleo/test_sprint3_h54.py`); se ejecutan dentro del contenedor `admin-api`.
> La migración **no se aplicó todavía**: requiere respaldo previo.

Solo `BIBLIOTECA` y `ADMIN` activos. Un `USUARIO` recibe `403`; sin token, `401`.
**Nada se borra**: la custodia pasa a `DISPUESTO`, el objeto a `OCULTO` y queda un acta.

## Plazo configurable

Variable de entorno `CUSTODIA_PLAZO_DIAS` del servicio `admin-api` (por defecto `60`, mínimo `1`).

## `GET /api/admin/custodias/vencidas/`

Custodias `EN_CUSTODIA` recibidas hace más del plazo, de la más antigua a la más reciente (hasta 200).

```json
{
  "plazoDias": 60,
  "total": 1,
  "resultados": [{
    "id": 7, "codigo": "FND-2026-0101", "estado": "EN_CUSTODIA",
    "objeto": { "id": 30, "nombre": "Mochila azul", "categoria": "Mochilas", "…": "…" },
    "fechaRecepcion": "2026-09-01T10:00:00Z",
    "diasEnCustodia": 70,
    "reclamosPendientes": 1,
    "bloqueada": false
  }]
}
```

`bloqueada: true` si hay un reclamo `ACEPTADO` (el dueño está por retirar el objeto). No incluye `tokenQr`.

## `POST /api/admin/custodias/{id}/disponer/`

```json
{ "destino": "DONADO", "motivo": "Sin reclamos tras el plazo", "confirmar": true }
```

- `destino`: `DONADO` o `DESCARTADO`. `motivo`: 10 a 1000 caracteres. `confirmar` debe ser `true`.
- El responsable y la fecha los pone el servidor (no se aceptan otros campos).
- Los reclamos `PENDIENTE` del objeto pasan a `RECHAZADO` con aviso «Reclamo cerrado» a cada persona.

Respuesta `201`: la custodia con `estado: "DISPUESTO"`, `disposicion` y `actaUrl`.

| Código | Cuándo |
| --- | --- |
| `400` | sin confirmar, destino o motivo inválido, campos extra, **plazo aún no vencido** |
| `404` | custodia inexistente |
| `409` | ya dispuesta (**repetición**), ya retirada o con reclamo aceptado |

## `GET /api/admin/custodias/{id}/acta/`

PDF A4 `ACTA-<código>.pdf` con código, objeto, días en custodia, plazo, destino, motivo, responsable,
fecha y líneas de firma. Sin correos ni teléfonos. `404` si no hay disposición.

## Cambios en datos

- `custodias.estado` admite `DISPUESTO`.
- Tabla nueva `disposiciones` (una por custodia, `codigo_acta` único).
- Migración idempotente: `database/migracion_h54_disposicion.sql`; `script.sql` actualizado.
- `GET /api/admin/custodias/?estado=DISPUESTO` lista lo dispuesto; cada custodia trae `disposicion`.

## Panel

Pantalla **Vencidos** (`/vencidos`, BIBLIOTECA y ADMIN): lista con plazo, formulario de destino y motivo,
confirmación y descarga del acta.

## Pruebas

- Django (`test_sprint3_h54.py`, 8): permisos, lista con plazo configurado, disposición con acta y sin borrar, plazo no vencido, repetición, reclamo aceptado, validaciones, acta inexistente.
- Panel (`Sprint3.test.jsx`, bloque H54, 3): lista y vacío, motivo y confirmación con acta, bloqueo y error del servidor.
