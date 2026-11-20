# H44 — Entregar el objeto escaneando el QR de retiro

Responsable de la API: Amore. Web: Yajaira (QR del titular en Detalle). Android: Amore
(`EscanerCustodia` del modo biblioteca y QR del titular). El QR se genera al aceptar el
reclamo en H43.

## `GET /api/objetos/{id}/reclamos/custodia/qr-retiro` (Spring)

Requiere sesión. Devuelve el QR solo al titular de un reclamo `ACEPTADO`, con el QR sin usar,
vigente y con el objeto todavía `EN_CUSTODIA`. Se responde con `Cache-Control: no-store`.

```json
{ "token": "<token URL-safe de 43 caracteres>", "expira": "2026-11-17T15:20:00" }
```

`expira` está en UTC (7 días desde la aceptación).

| Código | Caso |
| --- | --- |
| `200` | QR vigente |
| `400` | «El QR de retiro ya no está disponible»: no aceptado, usado, vencido o el objeto ya no está en custodia |
| `401` | Sin token |
| `404` | La cuenta no tiene reclamo sobre ese objeto |

## `POST /api/admin/reclamos/retirar/` (Django)

Requiere sesión de `BIBLIOTECA` o `ADMIN` (`EsBibliotecaOAdmin`).

```json
{ "token": "<token del QR de retiro>", "etiqueta": "<token de la etiqueta o el enlace completo de su QR>" }
```

- `token`: hasta 64 caracteres. `etiqueta`: hasta 1000 (acepta el enlace que trae el QR impreso de H41).
- Todo ocurre en una transacción: valida la etiqueta firmada, que el QR y la etiqueta sean
  del mismo objeto, marca el reclamo `RETIRADO`, el objeto `RECUPERADO` con `fecha_recuperacion`
  y consume el QR. Si algo falla, no cambia nada.

| Código | Caso |
| --- | --- |
| `200` | Retiro registrado; devuelve el reclamo |
| `400` | Faltan campos o la etiqueta de custodia no es válida |
| `401` / `403` | Sin token o rol sin permiso |
| `404` | El QR de retiro no es válido |
| `409` | Reclamo no aceptado o ya retirado; QR ya usado; QR vencido; objeto fuera de custodia; etiqueta de otro objeto |

## Verificación

- Django: `nucleo/test_sprint3_h43.py` (QR de un solo uso, vencido, token inválido y enlace de la etiqueta).
- Ver [entrega de Amore](../integracion/entrega-amore-sprint3.md): rechazo de QR cruzado y usado con la API real.
- La prueba del escáner en un teléfono físico queda en M2 del Sprint 4.
