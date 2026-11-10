# H41 — Recepción de objetos en custodia con QR

> Estado de esta entrega: API/datos implementados y probados en Django y Spring.
> Las pantallas web (Yajaira) y Android (Amore) quedan pendientes en sus capas.

## Permisos

Solo `BIBLIOTECA` y `ADMIN` activos usan esta API. Un `USUARIO` recibe `403`.
La recepción cambia el objeto a `EN_CUSTODIA` en la misma transacción; Spring
mantiene ese estado visible en el detalle y en las búsquedas públicas.

## `POST /api/admin/custodias/`

```json
{
  "objetoId": 12,
  "observaciones": "Entregado en el mostrador de la biblioteca"
}
```

- `objetoId` debe existir y ser de tipo `ENCONTRADO`.
- No se aceptan objetos `ELIMINADO` o `RECUPERADO`.
- Un objeto solo se registra una vez: el segundo intento responde `409`.
- El responsable y la fecha los pone el servidor desde el token.

Respuesta `201`:

```json
{
  "id": 5,
  "codigo": "FND-2026-0001",
  "objeto": {
    "id": 12,
    "nombre": "Mochila azul",
    "descripcion": "Mochila con cierre roto",
    "ubicacion": "Biblioteca central",
    "fecha_objeto": "2026-11-08",
    "tipo": "ENCONTRADO",
    "estado": "EN_CUSTODIA",
    "categoria": "Mochilas"
  },
  "recibidoPor": { "id": 3, "nombre": "Beto Biblioteca" },
  "fechaRecepcion": "2026-11-09T15:04:05Z",
  "observaciones": "Entregado en el mostrador de la biblioteca",
  "estado": "EN_CUSTODIA",
  "tokenQr": "<jwt firmado>",
  "etiquetaUrl": "/api/admin/custodias/5/etiqueta/",
  "constanciaUrl": "/api/admin/custodias/5/constancia/"
}
```

Errores: `400` objeto inválido o tipo distinto de `ENCONTRADO`; `404` objeto
inexistente; `409` objeto ya en custodia; `403` rol sin permiso.

## `GET /api/admin/custodias/`

Lista paginada de 10 en 10. Filtros opcionales:

| Parámetro | Ejemplo | Efecto |
| --- | --- | --- |
| `q` | `Mochila` | busca por código o nombre del objeto |
| `estado` | `EN_CUSTODIA` / `RETIRADO` | filtra por estado de custodia |
| `categoriaId` | `2` | filtra por categoría |
| `desde` | `2026-11-01` | fecha de recepción desde |
| `hasta` | `2026-11-09` | fecha de recepción hasta |

## `GET /api/admin/custodias/{id}/`

Mismo formato que la respuesta de creación.

## Etiqueta PDF

`GET /api/admin/custodias/{id}/etiqueta/` devuelve `application/pdf` de 10 × 7 cm
con el código, el QR firmado y el nombre del objeto. **No incluye nombre, correo
ni teléfono del autor o del reclamante.**

## Constancia PDF

`GET /api/admin/custodias/{id}/constancia/` devuelve un A4 con código, objeto,
categoría, descripción, ubicación, fecha, responsable, observaciones y líneas de
firma. Se entrega a quien deja el objeto. Tampoco expone el correo del autor.

## Verificar el QR

`GET /api/admin/custodias/verificar/?token=<jwt>`

```json
{
  "valido": true,
  "codigo": "FND-2026-0001",
  "estado": "EN_CUSTODIA",
  "objeto": { "id": 12, "nombre": "Mochila azul", "estado": "EN_CUSTODIA" },
  "fechaRecepcion": "2026-11-09T15:04:05+00:00"
}
```

Token alterado o ausente: `400`. Token válido sin custodia asociada: `404`.

## Formato del código y del token

- Código: `FND-<año>-<consecutivo de 4 dígitos>`, único por año. Se genera con
  `secuencias_custodia` y bloqueo de fila.
- Token QR: JWT HS256 firmado con `JWT_SECRET`, con `sub` = código,
  `objeto` = id del objeto, `tipo` = `custodia` e `iat`. No expira porque es una
  etiqueta física; no contiene datos personales.

## Datos

- `objetos.estado` admite `EN_CUSTODIA`.
- `custodias`: `objeto_id` único, `codigo` único, `token_qr`, `recibido_por_id`,
  `fecha_recepcion`, `observaciones`, `estado`.
- `secuencias_custodia`: consecutivo por año.
- Migración: `database/migracion_h41_custodia.sql`.
