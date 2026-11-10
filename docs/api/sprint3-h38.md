# H38 — Respaldar los datos automáticamente

> Estado de esta entrega: script, servicio de Docker Compose, tabla de registro,
> API de consulta y pruebas de Django implementados. La verificación de extremo
> a extremo con MySQL/Docker queda pendiente en este equipo porque el daemon de
> Docker no estaba disponible; no se declara como ejecutada.

## Alcance

Se respalda **solo la base de datos** `objetos_perdidos_db` mediante un volcado
lógico (`mysqldump`). Los archivos subidos por los usuarios (`uploads_data`)
quedan fuera de H38 y deben respaldarse por separado.

El respaldo:

- usa `--single-transaction` para no bloquear las tablas InnoDB;
- se comprime con gzip;
- se verifica con SHA-256 antes de publicarlo;
- se registra en la tabla `respaldos` con estado `EN_PROCESO`, `COMPLETADO` o
  `FALLIDO`;
- se escribe en el volumen `respaldos_data`, **nunca** en `mysql_data`.

## Frecuencia y retención

| Variable | Valor por defecto | Significado |
| --- | --- | --- |
| `RESPALDO_INTERVALO_SEGUNDOS` | `86400` | espera entre respaldos (24 h) |
| `RESPALDO_RETENCION_DIAS` | `7` | borra archivos con más de 7 días; `0` conserva todos |
| `RESPALDO_DIR` | `/respaldos` | carpeta del volumen |
| `RESPALDO_UNA_VEZ` | `0` | `1` ejecuta un solo respaldo y termina |

El servicio arranca con `restart: unless-stopped` y ejecuta el primer respaldo
al iniciar. La retención elimina únicamente archivos del volumen de respaldos;
las filas de `respaldos` se conservan como historial.

## Archivos generados

```text
foundia_objetos_perdidos_db_20261109T030000Z.sql.gz
foundia_objetos_perdidos_db_20261109T030000Z.sql.gz.sha256
```

El `.sha256` contiene la huella del archivo comprimido. El nombre usa la fecha
UTC para que no dependa de la zona horaria del contenedor.

## Servicio de Docker Compose

```bash
docker compose up -d mysql respaldo
docker compose logs -f respaldo
```

Ejecución manual de un solo respaldo:

```bash
docker compose run --rm -e RESPALDO_UNA_VEZ=1 respaldo
```

El servicio `respaldo` declara `depends_on: mysql: condition: service_healthy`
y monta solo `respaldos_data:/respaldos`. No recibe el volumen `mysql_data` ni
ejecuta `docker compose down -v`.

## Tabla `respaldos`

```sql
CREATE TABLE respaldos (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo VARCHAR(255) NOT NULL,
    huella_sha256  CHAR(64)     NULL,
    tamano_bytes   BIGINT       NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'EN_PROCESO',
    iniciado_en    DATETIME(6)  NOT NULL,
    finalizado_en  DATETIME(6)  NULL,
    mensaje        VARCHAR(500) NULL,
    CONSTRAINT uk_respaldo_archivo UNIQUE (nombre_archivo),
    CONSTRAINT chk_respaldo_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADO', 'FALLIDO')),
    INDEX idx_respaldos_iniciado (iniciado_en)
);
```

En una base existente se aplica
[`database/migracion_h38_respaldos.sql`](../../database/migracion_h38_respaldos.sql).
Es idempotente y no toca los datos existentes.

## API del panel

Todos los endpoints requieren `Authorization: Bearer <token>` y rol `ADMIN`.

| Método | Ruta | Respuesta |
| --- | --- | --- |
| `GET` | `/api/admin/respaldos/` | historial paginado, más reciente primero |
| `GET` | `/api/admin/respaldos/{id}/` | detalle de un respaldo |
| `GET` | `/api/admin/respaldos/ultimo/` | `{"ultimo": {...}}` o `{"ultimo": null}` |

Campos de cada respaldo:

```json
{
  "id": 12,
  "nombreArchivo": "foundia_objetos_perdidos_db_20261109T030000Z.sql.gz",
  "huellaSha256": "9f2c...64 caracteres...",
  "tamanoBytes": 184320,
  "estado": "COMPLETADO",
  "iniciadoEn": "2026-11-09T03:00:00Z",
  "finalizadoEn": "2026-11-09T03:00:04Z",
  "mensaje": null
}
```

Es un recurso de **solo lectura**: `POST`, `PUT`, `PATCH` y `DELETE` responden
`405`. La fila la escribe el servicio de respaldo.

## Restauración en una base aislada

`scripts/restaurar_respaldo.sh` restaura un `.sql.gz` verificado en una base
distinta de la de producción. Nunca se ejecuta automáticamente.

```bash
DB_NAME=foundia_restauracion_prueba \
DB_HOST=127.0.0.1 \
RESPALDO_ARCHIVO=/ruta/foundia_objetos_perdidos_db_20261109T030000Z.sql.gz \
bash scripts/restaurar_respaldo.sh
```

Guardas:

1. exige `DB_NAME`, valida que sea un nombre simple y rechaza las bases del
   sistema;
2. rechaza `objetos_perdidos_db` salvo `RESTAURACION_PERMITIR_PRODUCCION=si`;
3. verifica el `.sha256` (o `RESPALDO_SHA256`) **antes** de conectarse;
4. si la base destino ya tiene tablas, exige
   `RESTAURACION_PERMITIR_SOBREESCRITURA=si` y la recrea;
5. al terminar comprueba que la base restaurada tenga tablas y las lista.

Para una prueba real se recomienda una base desechable, por ejemplo
`foundia_restauracion_prueba`, y nunca apuntar el script al volumen
`mysql_data` en uso.

## Pruebas

- `nucleo/test_sprint3_h38.py` (**6 pruebas**): permisos (401/403/200), solo
  lectura (405), orden del historial, contrato en camelCase, detalle,
  `ultimo/` vacío y `ultimo/` con un respaldo fallido.
- `bash -n scripts/respaldo.sh` y `bash -n scripts/restaurar_respaldo.sh`:
  sintaxis válida.
- Guardas del restaurador comprobadas sin MySQL: nombre inválido, base de
  producción sin confirmación y huella SHA-256 alterada.
- **Pendiente de verificar con Docker/MySQL:** ejecución real del respaldo,
  contenido restaurado y retención de archivos. El daemon de Docker no estaba
  disponible al preparar esta entrega.

## Tareas de otras capas

- **Web (Yajaira):** tarjeta «Último respaldo» con estado, fecha, tamaño y
  mensaje de error; consumir `GET /api/admin/respaldos/ultimo/` y mostrar un
  aviso si `ultimo` es `null`. No se modificó el frontend.
- **Android (Amore):** sin cambios; H38 es infraestructura y panel.
