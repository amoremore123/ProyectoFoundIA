# H60 — Campañas de difusión

Responsable del backend: Leonel (Django). Web: Yajaira (panel de campañas: lista, alta y edición).
Backlog 15.1. Base de H66 (promociones en redes), H67 (alcance por origen) y H71 (avisos destacados).

Una campaña es una acción de difusión con fechas y un **enlace con origen**: la URL de la web con
`?origen=<slug>`, para que H67 pueda contar cuántas visitas y registros llegaron por ella.

## Rutas (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/campanas/?estado=PROGRAMADA\|ACTIVA\|FINALIZADA` | Lista paginada, la más reciente primero |
| `POST` | `/api/admin/campanas/` | Crear |
| `GET` | `/api/admin/campanas/{id}/` | Detalle |
| `PATCH` / `PUT` | `/api/admin/campanas/{id}/` | Editar |
| `POST` | `/api/admin/campanas/{id}/finalizar/` | Terminar una campaña `ACTIVA` hoy: `fechaFin` pasa a la fecha actual y queda `FINALIZADA`. Una `PROGRAMADA` responde `400` (se cambian sus fechas). Repetirlo sobre una `FINALIZADA` no cambia nada |
| `DELETE` | `/api/admin/campanas/{id}/` | **No se permite** (`405`): las campañas no se borran |

### Cuerpo de `POST` y `PATCH`

```json
{
  "nombre": "Semana de objetos perdidos",
  "descripcion": "Difusión en los pabellones y redes del campus",
  "origen": "semana-perdidos-2026",
  "destino": "/",
  "fechaInicio": "2026-11-24",
  "fechaFin": "2026-11-30"
}
```

| Campo | Regla |
| --- | --- |
| `nombre` | Obligatorio, 3 a 120 caracteres |
| `descripcion` | Opcional, hasta 500 caracteres |
| `origen` | Obligatorio, **único**, 3 a 40 caracteres: minúsculas, números y guiones (`semana-perdidos-2026`). No se puede cambiar después de creada: el enlace ya pudo compartirse |
| `destino` | Opcional, ruta de la web que abre el enlace, empieza con `/` (por defecto `/`). No acepta otros dominios |
| `fechaInicio` | Obligatoria (`AAAA-MM-DD`) |
| `fechaFin` | Obligatoria, igual o posterior a `fechaInicio` |

### Respuesta

```json
{
  "id": 3,
  "nombre": "Semana de objetos perdidos",
  "descripcion": "Difusión en los pabellones y redes del campus",
  "origen": "semana-perdidos-2026",
  "destino": "/",
  "enlace": "http://localhost:5173/?origen=semana-perdidos-2026",
  "fechaInicio": "2026-11-24",
  "fechaFin": "2026-11-30",
  "estado": "PROGRAMADA",
  "finalizadaEn": null,
  "creadaPor": { "id": 1, "nombre": "Admin Sistema" },
  "fechaCreacion": "2026-11-20T15:20:00Z",
  "fechaActualizacion": "2026-11-20T15:20:00Z"
}
```

- `enlace` se arma en el servidor con `FOUNDIA_WEB_URL` + `destino` + `?origen=…`. La web lo copia tal cual.
- `estado` se calcula con la fecha de hoy **en Lima** (UTC−5; el servidor trabaja en UTC):
  `PROGRAMADA` (antes de `fechaInicio`), `ACTIVA` (entre las dos fechas, inclusive) o `FINALIZADA`
  (después de `fechaFin`, o si se finalizó antes con `finalizar/`; `finalizadaEn` guarda cuándo).
- `creadaPor` y las fechas de creación y actualización las pone el servidor: no se aceptan en el cuerpo.
- El enlace **no lleva datos personales** ni de quién creó la campaña.

| Código | Caso |
| --- | --- |
| `200` / `201` | Lista, detalle, edición / creación |
| `400` | Campo inválido, `origen` repetido o con formato inválido, `fechaFin` anterior a `fechaInicio`, intento de cambiar `origen`, `estado` de filtro inválido |
| `401` / `403` | Sin sesión / rol distinto de `ADMIN` (incluida `BIBLIOTECA`) |
| `404` | La campaña no existe |
| `405` | `DELETE` |

## Base de datos

`database/migracion_h60_campanas.sql` (idempotente; también en `script.sql`):

```sql
CREATE TABLE IF NOT EXISTS campanas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  descripcion VARCHAR(500) NULL,
  origen VARCHAR(40) NOT NULL UNIQUE,
  destino VARCHAR(255) NOT NULL DEFAULT '/',
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  creada_por_id BIGINT NOT NULL,
  finalizada_en DATETIME(6) NULL,
  fecha_creacion DATETIME(6) NOT NULL,
  fecha_actualizacion DATETIME(6) NOT NULL,
  CHECK (fecha_fin >= fecha_inicio)
);
```

## Verificación del 20/11/2026

- Django: `nucleo/test_sprint4_h60.py` (**8 pruebas**): permisos, autor y enlace del servidor, origen
  único y sin cambios, fechas y destinos inválidos, estado y filtro, día de Lima frente a UTC,
  `finalizar/` y `DELETE` → `405`.
- MySQL 8.0 de QA: `migracion_h60_campanas.sql` se ejecutó dos veces sin error. Por la API real:
  crear `201` con `enlace` y `creadaPor` del servidor; origen repetido, fechas invertidas y cambio de
  origen `400`; `DELETE` `405`; una campaña activa quedó `FINALIZADA` con `fechaFin` de hoy y
  `finalizadaEn`; una programada respondió `400` al finalizarla.
- La prueba confirmó la hora de Lima: una campaña que empieza el 20/11 salió `PROGRAMADA` a las
  02:00 UTC del 20/11, porque en Lima todavía era 19/11.

## Pruebas que se deben ejecutar y registrar

- Solo `ADMIN` lista, crea y edita; `BIBLIOTECA` y `USUARIO` reciben `403`; sin sesión `401`.
- `origen` repetido, con mayúsculas o espacios: `400`. Cambiar `origen` al editar: `400`.
- `fechaFin` anterior a `fechaInicio`: `400`, también al editar solo una de las dos fechas.
- `destino` con otro dominio (`https://…` o `//…`): `400`.
- `estado` correcto antes, durante y después de las fechas; `finalizar/` deja `FINALIZADA` una
  `ACTIVA`, responde `400` a una `PROGRAMADA` y no cambia una ya finalizada.
- El filtro `?estado=` coincide con el `estado` de cada campaña, incluidas las finalizadas antes de tiempo.
- `enlace` usa `FOUNDIA_WEB_URL` y el `origen`; `creadaPor` es quien creó, no un valor enviado.
- `DELETE` responde `405` y la campaña sigue existiendo.
