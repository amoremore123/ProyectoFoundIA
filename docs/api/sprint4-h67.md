# H67 — Medir el alcance de las campañas

Responsable del backend: Leonel (Spring para contar, Django para las métricas). Web: Yajaira (guardar el
origen del enlace y el tablero de alcance en el panel). Backlog 15.3. Prioridad «Debería». Usa las campañas
de H60 y los avisos destacados de H71.

Cada campaña tiene un enlace con origen (`/explorar?origen=semana-perdidos-2026`, H60). H67 cuenta, por
campaña, **cuántas visitas** llegaron por ese enlace y **cuántas cuentas se registraron** después de llegar
por él. Como pide el Sprint 4: una visita suma **una vez** y el conteo **no guarda datos personales**.

## Qué se guarda (y qué no)

Cada visita o registro es una fila con: la campaña, el tipo (`VISITA` o `REGISTRO`), la fecha y, para las
visitas, un identificador al azar que genera la web. **No se guarda** la IP, el navegador, el correo ni qué
cuenta se registró: un registro solo suma 1 a la campaña.

## Contar (Spring, sin sesión)

### Visita

`POST /api/campanas/visitas`

```json
{ "origen": "semana-perdidos-2026", "visitaId": "6f1c2b9e-4a7d-4f1e-9c3b-2d5e8a7b1c40" }
```

- La web la envía al abrir una página con `?origen=`. `visitaId` lo genera la web (un UUID) **una vez por
  visita** y lo guarda en `sessionStorage`: si la persona recarga la página o vuelve a entrar en la misma
  pestaña, se manda el mismo `visitaId` y **no suma otra vez**.
- Responde **`204`** siempre que el formato sea válido, aunque el origen no exista o la visita ya se haya
  contado: así nadie puede averiguar qué orígenes existen probando.
- Cuenta aunque la campaña esté finalizada (el enlace pudo quedar compartido).

| Código | Caso |
| --- | --- |
| `204` | Recibido |
| `400` | `origen` sin el formato de H60 (minúsculas, números y guiones, 3 a 40) o `visitaId` que no es de 8 a 64 letras, números, `-` o `_` |
| `429` | Más de 30 visitas por minuto desde el mismo equipo (el límite vive en memoria; la IP no se guarda) |

### Registro

`POST /api/auth/register` acepta un campo opcional `"origen"`. Si la cuenta se crea y el origen es de una
campaña, suma un registro. Si el origen no existe o no tiene el formato, **el registro sigue igual** (no da
error) y no se cuenta. Si el registro falla (por ejemplo, el correo no se pudo enviar), tampoco se cuenta.

## Métricas (Django, solo `ADMIN`)

Fechas del calendario de Lima, como en H60. Rango de hasta 366 días.

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/campanas/alcance/?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` | Tablero: visitas y registros de **cada** campaña en el rango (por defecto, los últimos 30 días) |
| `GET` | `/api/admin/campanas/{id}/alcance/?desde=&hasta=` | Una campaña, con el detalle por día (por defecto, de su inicio a hoy o a su fin) |

```json
GET /api/admin/campanas/alcance/?desde=2026-11-01&hasta=2026-11-30
{
  "desde": "2026-11-01",
  "hasta": "2026-11-30",
  "campanas": [
    { "id": 3, "nombre": "Semana de objetos perdidos", "origen": "semana-perdidos-2026",
      "estado": "ACTIVA", "visitas": 128, "registros": 9, "conversion": 7.0 }
  ],
  "total": { "visitas": 128, "registros": 9 }
}
```

```json
GET /api/admin/campanas/3/alcance/
{
  "campana": { "id": 3, "nombre": "Semana de objetos perdidos", "origen": "semana-perdidos-2026" },
  "desde": "2026-11-24",
  "hasta": "2026-11-26",
  "visitas": 40,
  "registros": 3,
  "conversion": 7.5,
  "porDia": [
    { "fecha": "2026-11-24", "visitas": 12, "registros": 1 },
    { "fecha": "2026-11-25", "visitas": 28, "registros": 2 },
    { "fecha": "2026-11-26", "visitas": 0, "registros": 0 }
  ]
}
```

- `conversion` = registros ÷ visitas × 100, con un decimal (`0.0` si no hubo visitas).
- `porDia` trae todos los días del rango, también los que tienen 0.
- Errores: `400` si una fecha no es `AAAA-MM-DD`, si `desde` es posterior a `hasta` o si el rango pasa de 366
  días; `401`, `403` y `404` como en H60.

## Base de datos

`database/migracion_h67_alcance_campanas.sql` (idempotente, también en `script.sql`):

```sql
CREATE TABLE IF NOT EXISTS alcance_campanas (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campana_id BIGINT      NOT NULL,       -- FK campanas
    tipo       VARCHAR(10) NOT NULL,       -- VISITA | REGISTRO
    visita_id  VARCHAR(64) NULL,           -- solo en las visitas; al azar, de la web
    fecha      DATETIME(6) NOT NULL,       -- UTC
    UNIQUE (campana_id, visita_id)         -- la misma visita no suma dos veces
);
```

## Lo que necesita la web (Yajaira)

1. Al abrir cualquier página con `?origen=`: guardar el origen y un `visitaId` (`crypto.randomUUID()`) en
   `sessionStorage` si todavía no hay uno para ese origen, y enviar `POST /api/campanas/visitas`. Un error de
   esta llamada no debe molestar a la persona.
2. En el registro, enviar `"origen"` con el valor guardado (si hay).
3. Panel: tablero con rango de fechas (`/api/admin/campanas/alcance/`) y detalle por día de cada campaña.
   Los números del tablero deben ser los mismos que devuelve la API.

Android no necesita cambios: los enlaces de campaña se abren en la web.

## Pruebas que se deben ejecutar y registrar

- Visita: suma una vez; repetir el mismo `visitaId` no suma; otro `visitaId` sí; origen inexistente `204` sin
  contar; campaña finalizada sí cuenta; formato inválido `400`; más de 30 por minuto `429`; sin sesión.
- No se guarda la IP ni nada de la persona (revisar las columnas de la tabla).
- Registro: con origen válido suma 1; sin origen, con origen inexistente o mal escrito el registro funciona y no
  suma; si el registro falla, no suma.
- Métricas: tablero y detalle por rango, por día con ceros, conversión, por defecto, fechas inválidas o rango
  invertido o mayor de 366 días `400`, días de Lima y no de UTC; `401`, `403`, `404`.
- Con MySQL real: la migración dos veces; visitas y un registro por la API de Spring y los mismos números en el
  tablero de Django.

## Importante para el despliegue (C1)

El límite de 30 visitas por minuto usa la dirección de quien llama. Detrás del proxy inverso con HTTPS (C1)
todas las visitas llegarían desde la dirección del proxy y compartirían el mismo límite. Al desplegar hay que
configurar Spring para leer la dirección real (`server.forward-headers-strategy: framework` y que el proxy envíe
`X-Forwarded-For`). Se deja anotado para C1.

## Verificación del 23/11/2026

- Spring: 8 pruebas nuevas en `AlcanceCampanasIntegrationTest` (suma una vez y el mismo `visitaId` no vuelve a
  sumar; origen inexistente `204` sin contar; campaña finalizada sí cuenta; formatos inválidos `400`; 31.ª visita
  del minuto `429` y otro equipo sigue; la tabla solo tiene `id`, `campana_id`, `tipo`, `visita_id` y `fecha`;
  registro con origen suma sin guardar la cuenta; sin origen, inexistente, mal escrito o `null` registra y no
  suma; registro fallido no suma). Suite completa: **589 pruebas, 0 fallos**.
- Django: 8 pruebas nuevas en `test_sprint4_h67.py` (tablero con conversión, total y orden; rango; detalle por día
  con ceros y rango por defecto, también de una campaña terminada; días de Lima y no de UTC; fechas inválidas,
  invertidas o de más de 366 días; `401`, `403` y `404`; sin datos personales en la respuesta). Suite completa:
  **188 pruebas, OK**.
- MySQL 8.0 de QA, con Spring y Django reales:

| Caso | Resultado |
| --- | --- |
| Migración | `migracion_h67_alcance_campanas.sql` dos veces sin error |
| Columnas | Solo `id`, `campana_id`, `tipo`, `visita_id`, `fecha`. La base rechaza un registro con `visita_id` (`ERROR 3819`) |
| Campaña creada en el panel | Enlace `…/explorar?origen=semana-h67` |
| 3 visitas + la misma visita repetida | `204` las cuatro; se contaron **3** |
| Origen inexistente | `204`, no se contó |
| Formato inválido | `400` con el campo |
| Registro con origen / sin origen / con origen inexistente | `201` los tres; se contó **1** registro |
| Tablero (Django) | Semana H67: 3 visitas, 1 registro, 33.3 %: **los mismos números** que guardó Spring |
| Detalle | Mismo total, y el día de hoy con 3 y 1 |
| Fechas al revés / usuaria normal | `400` / `403` |
| Límite | La 31.ª visita del minuto desde el mismo equipo respondió `429` |
| Instalación nueva | `script.sql`, `seed.sql` y la migración sobre esa base, sin errores |

- **Falta en la web (Yajaira):** guardar el origen y enviar la visita al abrir un enlace de campaña, mandar
  `origen` en el registro y el tablero en el panel (ver «Lo que necesita la web»). Sin eso, la API funciona pero
  nadie la llama.
