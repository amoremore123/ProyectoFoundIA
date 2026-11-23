# H71 — Avisos destacados

Responsable del backend: Leonel (Django para administrar, Spring para la API pública). Web: Yajaira (alta y
edición en el panel; los avisos vigentes arriba en la web de usuario). Android: Amore (los avisos vigentes
arriba en Inicio). Backlog 15.5. Prioridad «Si sobra». Usa las campañas de H60.

Un **aviso destacado** es un mensaje corto que el administrador quiere que todos vean arriba de la web y de
la app durante unos días: «Semana de objetos perdidos: revisa la mesa de la biblioteca», «La biblioteca
atiende hasta las 6 p. m. el viernes». Tiene **vigencia**: fuera de sus fechas no se muestra, sin cambiar
código ni volver a publicar nada.

## Vigencia

Las fechas son del calendario de Lima (UTC−5, sin horario de verano), como las campañas de H60, y las dos se
incluyen.

| Estado | Cuándo | ¿Se muestra? |
| --- | --- | --- |
| `PROGRAMADO` | Hoy es antes de `fechaInicio` | No |
| `VIGENTE` | `fechaInicio` ≤ hoy ≤ `fechaFin` y no se retiró | **Sí** |
| `VENCIDO` | Hoy es después de `fechaFin` | No |
| `RETIRADO` | El administrador lo retiró antes de tiempo | No, desde ese momento |

## Administración (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/avisos-destacados/?estado=PROGRAMADO\|VIGENTE\|VENCIDO\|RETIRADO` | Lista paginada, el más reciente primero |
| `POST` | `/api/admin/avisos-destacados/` | Crear |
| `GET` | `/api/admin/avisos-destacados/{id}/` | Detalle |
| `PATCH` / `PUT` | `/api/admin/avisos-destacados/{id}/` | Editar (uno `RETIRADO` no se edita: `400`) |
| `POST` | `/api/admin/avisos-destacados/{id}/retirar/` | Dejar de mostrarlo ya. Repetirlo no cambia nada |
| `DELETE` | `/api/admin/avisos-destacados/{id}/` | **No se permite** (`405`): queda el historial |

```json
{
  "titulo": "Semana de objetos perdidos",
  "texto": "Del 24 al 30 de noviembre revisa la mesa de objetos en la biblioteca.",
  "destino": "/explorar",
  "campanaId": null,
  "fechaInicio": "2026-11-24",
  "fechaFin": "2026-11-30"
}
```

| Campo | Regla |
| --- | --- |
| `titulo` | Obligatorio, 3 a 80 caracteres |
| `texto` | Obligatorio, 3 a 300 caracteres |
| `destino` | Opcional: ruta de la web que abre el aviso, empieza con `/`, sin dominio ni parámetros |
| `campanaId` | Opcional: campaña de H60. El aviso abre el enlace de la campaña (con su `?origen=`), así H67 cuenta las visitas que llegan por el aviso. No se usa junto con `destino` |
| `fechaInicio`, `fechaFin` | Obligatorias (`AAAA-MM-DD`); `fechaFin` igual o posterior a `fechaInicio` |

La respuesta agrega `id`, `enlace` (la ruta que se abre, o `null`), `estado`, `retiradoEn`, `creadoPor`,
`fechaCreacion` y `fechaActualizacion`.

Errores: `400` con el campo y el motivo, `401` sin sesión, `403` si no es `ADMIN`, `404` si no existe.

## API pública (Spring, sin sesión)

`GET /api/avisos-destacados` devuelve **solo los vigentes hoy**, como máximo 5, el que empezó más
recientemente primero. Sin datos de quién los creó.

```json
[
  {
    "id": 3,
    "titulo": "Semana de objetos perdidos",
    "texto": "Del 24 al 30 de noviembre revisa la mesa de objetos en la biblioteca.",
    "enlace": "/explorar?origen=semana-perdidos-2026",
    "fechaFin": "2026-11-30"
  }
]
```

- `enlace` es una ruta de la web (o `null`): la web la abre tal cual y Android la puede abrir en su WebView o
  en la pantalla equivalente.
- Sin avisos vigentes devuelve `[]`.

## Base de datos

`database/migracion_h71_avisos_destacados.sql` (idempotente, también en `script.sql`):

```sql
CREATE TABLE IF NOT EXISTS avisos_destacados (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(80)  NOT NULL,
    texto               VARCHAR(300) NOT NULL,
    destino             VARCHAR(255) NULL,
    campana_id          BIGINT       NULL,
    fecha_inicio        DATE         NOT NULL,
    fecha_fin           DATE         NOT NULL,
    retirado_en         DATETIME(6)  NULL,
    creado_por_id       BIGINT       NOT NULL,
    fecha_creacion      DATETIME(6)  NOT NULL,
    fecha_actualizacion DATETIME(6)  NOT NULL,
    FOREIGN KEY (campana_id) REFERENCES campanas (id),
    FOREIGN KEY (creado_por_id) REFERENCES usuarios (id)
);
```

## Lo que necesitan la web y Android

- **Panel (Yajaira):** lista con filtro por estado, alta y edición con los errores junto a cada campo, y el
  botón «Retirar».
- **Web de usuario (Yajaira) y Android (Amore):** pedir `GET /api/avisos-destacados` al abrir Inicio y mostrar
  los avisos arriba. No guardarlos en caché más de un día: uno vencido no debe seguir apareciendo.

## Pruebas que se deben ejecutar y registrar

- Django: crear, editar y retirar; validaciones (`titulo`, `texto`, fechas, `destino` externo, `destino` y
  `campanaId` juntos, campaña inexistente); estado por fechas en Lima; filtro por estado; un retirado no se
  edita; `DELETE` `405`; `401` y `403`.
- Spring: solo los vigentes (no los programados, vencidos ni retirados), el último día todavía se muestra,
  máximo 5 y en orden, enlace de la campaña con `?origen=`, sin sesión `200`, sin avisos `[]`.
- Con MySQL real: la migración dos veces; un aviso creado en el panel aparece en la API pública y deja de
  aparecer al retirarlo o al vencer.
