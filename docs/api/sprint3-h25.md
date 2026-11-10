# H25 — Historial de notificaciones leídas y no leídas

> Estado: API (Spring) y web implementadas y probadas. La capa Android (Amore) consume este contrato.

`GET /api/notificaciones` **no cambia**: sigue devolviendo la lista completa (la campana la usa).

## `GET /api/notificaciones/historial`

Cabecera `Authorization: Bearer <token>`. Solo devuelve avisos de la cuenta del token.

| Parámetro | Valores | Por defecto |
| --- | --- | --- |
| `estado` | `TODAS`, `NO_LEIDAS`, `LEIDAS` | `TODAS` |
| `pagina` | desde `0` | `0` |
| `tamano` | `1` a `50` | `20` |

Orden: del más reciente al más antiguo. Consultar **no** marca nada como leído.

```json
{
  "contenido": [
    { "id": 9, "titulo": "Nuevo reclamo de objeto", "mensaje": "…", "tipo": "RECLAMO_RECIBIDO",
      "leida": false, "fechaCreacion": "2026-11-10T10:30:00", "enlace": null }
  ],
  "pagina": 0,
  "tamano": 20,
  "total": 25,
  "totalPaginas": 2,
  "estado": "NO_LEIDAS",
  "noLeidas": 3
}
```

`noLeidas` cuenta todos los avisos sin leer de la cuenta, no solo los de la página.

Errores: `400` filtro, página o tamaño inválidos; `401` sin sesión.

## `PUT /api/notificaciones/leidas`

Marca como leídos todos los avisos de la cuenta del token (no recibe id de usuario). Repetirlo no cambia nada.

```json
{ "actualizadas": 3, "noLeidas": 0 }
```

## Pruebas

- Spring: `HistorialNotificacionesIntegrationTest` (6): sin sesión, filtro sin efectos, paginación, valores inválidos, solo avisos propios, «marcar todas» solo en la cuenta propia y repetible.
- Web: `Notificaciones.test.jsx` (bloque H25): filtros con su vacío, «marcar todas» con éxito y con error, «Ver más avisos» sin repetir.
