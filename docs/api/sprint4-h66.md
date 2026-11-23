# H66 — Promociones en redes sociales

Responsable del backend: Leonel (Django). Web: Yajaira (preparar el texto y el enlace y registrar la
publicación, en el panel de campañas). Backlog 15.2. Prioridad «Debería». Usa las campañas de H60 y el conteo
de H67.

Como pide el Sprint 4: **registrar la promoción publicada en redes (texto, enlace y fecha), sin conectar las
APIs de las redes**. FoundIA no publica nada por su cuenta: prepara un texto con el enlace de la campaña, la
persona lo publica a mano en la red, y después registra en el panel qué publicó, dónde y cuándo.

## Cómo funciona

1. **Preparar:** el panel pide una sugerencia para una campaña y una red. El servidor arma un texto con el
   nombre y la descripción de la campaña y su **enlace con origen** (H60), respetando el largo máximo de esa red.
   El texto se puede editar antes de publicarlo.
2. **Publicar:** la persona copia el texto y lo publica en la red (fuera de FoundIA).
3. **Registrar:** en el panel se guarda la promoción: red, texto publicado, fecha y hora, y opcionalmente la
   dirección de la publicación. El enlace se guarda tal como lo arma el servidor; no se escribe a mano.

Las visitas que llegan por ese enlace las cuenta H67 en la campaña.

## Redes

| `red` | Largo máximo del texto | Dominios aceptados en `urlPublicacion` |
| --- | --- | --- |
| `FACEBOOK` | 2200 | `facebook.com`, `fb.com`, `fb.watch` |
| `INSTAGRAM` | 2200 | `instagram.com` |
| `TIKTOK` | 2200 | `tiktok.com` |
| `X` | 280 | `x.com`, `twitter.com` |
| `LINKEDIN` | 2200 | `linkedin.com` |
| `WHATSAPP` | 2200 | `whatsapp.com`, `wa.me` (en grupos o estados no suele haber dirección) |
| `OTRA` | 2200 | Cualquier `https://` |

## Rutas (Django, solo `ADMIN`)

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/admin/promociones/sugerencia/?campana={id}&red={red}` | Texto sugerido y enlace con origen. No guarda nada |
| `GET` | `/api/admin/promociones/?campana={id}&red={red}` | Promociones registradas, la más reciente primero (paginado; filtros opcionales) |
| `POST` | `/api/admin/promociones/` | Registrar una publicación |
| `GET` | `/api/admin/promociones/{id}/` | Detalle |
| `PATCH` / `PUT` | `/api/admin/promociones/{id}/` | Corregir el texto, la fecha o la dirección (la campaña y la red no cambian) |
| `DELETE` | `/api/admin/promociones/{id}/` | **No se permite** (`405`): queda el historial |

### Sugerencia

```json
GET /api/admin/promociones/sugerencia/?campana=3&red=X
{
  "campana": 3,
  "red": "X",
  "texto": "Semana de objetos perdidos. Del 24 al 30 de noviembre revisa la mesa de objetos de la biblioteca. Más información: https://foundia.tecsup.edu.pe/explorar?origen=semana-perdidos-2026 #FoundIA #Tecsup",
  "enlace": "https://foundia.tecsup.edu.pe/explorar?origen=semana-perdidos-2026",
  "maximo": 280
}
```

Si no entra en el máximo, se acorta la descripción con «…»; el enlace nunca se corta.

### Registrar

```json
POST /api/admin/promociones/
{
  "campanaId": 3,
  "red": "INSTAGRAM",
  "texto": "Semana de objetos perdidos: revisa la mesa de la biblioteca. Enlace en la bio.",
  "publicadaEn": "2026-11-24T15:30:00-05:00",
  "urlPublicacion": "https://www.instagram.com/p/C0abc123/"
}
```

| Campo | Regla |
| --- | --- |
| `campanaId` | Obligatorio, una campaña que exista. No cambia después |
| `red` | Obligatorio, de la tabla de redes. No cambia después |
| `texto` | Obligatorio, de 3 caracteres al máximo de la red. Puede no incluir el enlace (por ejemplo, «enlace en la bio») |
| `publicadaEn` | Obligatorio, con zona horaria. No puede ser futuro (se aceptan 5 minutos de diferencia de reloj) |
| `urlPublicacion` | Opcional, `https://` y de un dominio de esa red |

La respuesta agrega `id`, `enlace` (el de la campaña con su origen), `campana` (`id`, `nombre`, `origen`),
`registradaPor` y `fechaRegistro`.

Errores: `400` con el campo y el motivo, `401` sin sesión, `403` si no es `ADMIN`, `404` si no existe.

## Base de datos

`database/migracion_h66_promociones.sql` (idempotente, también en `script.sql`):

```sql
CREATE TABLE IF NOT EXISTS promociones_campana (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campana_id        BIGINT        NOT NULL,   -- FK campanas
    red               VARCHAR(10)   NOT NULL,
    texto             VARCHAR(2200) NOT NULL,
    enlace            VARCHAR(400)  NOT NULL,
    url_publicacion   VARCHAR(500)  NULL,
    publicada_en      DATETIME(6)   NOT NULL,   -- UTC
    registrada_por_id BIGINT        NOT NULL,   -- FK usuarios
    fecha_registro    DATETIME(6)   NOT NULL
);
```

## Lo que necesita la web (Yajaira)

- En el detalle de una campaña: elegir la red, pedir la sugerencia, mostrar el texto editable con el contador
  de caracteres de esa red y un botón «Copiar».
- Después de publicarlo, el formulario «Registrar publicación» (red, texto, fecha y hora, dirección opcional)
  con los errores junto a cada campo.
- La lista de promociones de la campaña.

## Pruebas que se deben ejecutar y registrar

- Sugerencia: incluye el enlace con origen; en `X` no pasa de 280 y acorta la descripción sin cortar el enlace;
  campaña inexistente o red inválida `400`; no guarda nada.
- Registrar: guarda texto, enlace generado por el servidor, fecha y autor; validaciones (red, largo por red,
  fecha futura o sin zona, dirección que no es `https` o de otra red, campaña inexistente).
- Corregir: se puede cambiar texto, fecha y dirección; la campaña y la red no; `DELETE` `405`.
- Lista con filtros por campaña y red; `401`, `403`, `404`.
- Con MySQL real: la migración dos veces y una promoción registrada que aparece en la lista.

## Verificación del 23/11/2026

- Django: 10 pruebas nuevas en `test_sprint4_h66.py` (sugerencia con el enlace de origen y sin guardar nada; en `X`
  no pasa de 280, acorta la descripción con «…» y no corta el enlace; sugerencia con datos inválidos `400`;
  registrar guarda texto, enlace, fecha y autor; el enlace lo pone el servidor aunque se mande otro; sin dirección
  de publicación; validaciones de red, largo por red, fecha futura, sin zona o mal escrita, dirección `http`, de
  otra red, de un dominio que imita a la red o con usuario y clave, campaña inexistente o faltante; 280 exactos en
  `X` y `OTRA` con cualquier `https`; corregir texto y dirección, pero no campaña ni red, y la dirección se valida
  con la red guardada; `DELETE` `405`; lista con filtros y orden; `401`, `403`, `404`). Suite completa:
  **198 pruebas, OK**.
- MySQL 8.0 de QA, con el panel (Django) real:

| Caso | Resultado |
| --- | --- |
| Migración | `migracion_h66_promociones.sql` dos veces sin error |
| Sugerencia para `X` | `200`, 94 caracteres, con el enlace `…/explorar?origen=semana-h67` |
| Registrar | `201`, con el enlace de la campaña, la fecha en UTC y el autor; la fila quedó en la base |
| Dirección de Instagram en una promoción de `X` | `400` con el motivo |
| Fecha sin zona horaria | `400` |
| Corregir el texto / cambiar la red | `200` / `400` |
| `DELETE` | `405` |
| Lista de la campaña | La promoción con el texto corregido |
| Instalación nueva | `script.sql`, `seed.sql` y la migración sobre esa base, sin errores |

- FoundIA no publica en ninguna red ni guarda claves de redes: solo prepara el texto y registra lo publicado,
  como pide el Sprint 4.
- **Falta en la web (Yajaira):** preparar el texto con el botón «Copiar», el formulario para registrar la
  publicación y la lista (ver «Lo que necesita la web»).
