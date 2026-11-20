# H59 — Respaldar también las fotos

Responsable del backend: Leonel. Web: Yajaira (panel de respaldos, junto con H58 y H70).
Backlog 14.4. Continúa H38 ([contrato](sprint3-h38.md)).

## Qué se respalda

| Tipo | Origen | Archivo | Frecuencia y retención |
| --- | --- | --- | --- |
| `BASE` | MySQL (`mysqldump`, como en H38) | `foundia_objetos_perdidos_db_<fecha>.sql.gz` | Las de H38: cada `RESPALDO_INTERVALO_SEGUNDOS` (24 h) y `RESPALDO_RETENCION_DIAS` (7 días) |
| `FOTOS` | Volumen `uploads_data` (fotos de publicaciones) | `foundia_fotos_<fecha>.tar.gz` | En la misma ejecución que la base, con la misma retención |

- Cada archivo tiene su `.sha256` al lado y se verifica antes de darlo por `COMPLETADO`.
- El servicio `respaldo` monta `uploads_data` **en solo lectura** (`/fotos:ro`): no puede modificar
  ni borrar las fotos en uso. Tampoco monta `mysql_data`.
- Si falla la base, igual se intenta el respaldo de fotos, y al revés; cada uno queda con su estado.
- Las fotos se copian tal como están en el volumen, sin datos de la base. Para recuperar una
  publicación completa hacen falta los dos respaldos (ver H58).
- `RESPALDO_FOTOS=0` desactiva el respaldo de fotos (por defecto `1`).

## Registro (`respaldos`)

Nueva columna `tipo` (`BASE` o `FOTOS`, por defecto `BASE` para las filas de H38).
Migración idempotente: `database/migracion_h59_respaldo_fotos.sql` (también en `script.sql`).

## API (Django, solo `ADMIN`)

### `GET /api/admin/respaldos/?tipo=BASE|FOTOS`

Igual que en H38, con el campo nuevo `tipo` en cada elemento. Sin `tipo` lista los dos.

### `GET /api/admin/respaldos/ultimo/?tipo=BASE|FOTOS`

- **Sin `tipo` devuelve el último respaldo de la base**, igual que antes de H59, para que la
  tarjeta «Último respaldo» del panel siga mostrando lo mismo.
- Con `tipo=FOTOS` devuelve el último respaldo de fotos, o `{"ultimo": null}` si aún no hay.

```json
{ "ultimo": { "id": 8, "tipo": "FOTOS", "nombreArchivo": "foundia_fotos_20261120T030000Z.tar.gz",
  "huellaSha256": "…", "tamanoBytes": 1708400, "estado": "COMPLETADO",
  "iniciadoEn": "…", "finalizadoEn": "…", "mensaje": null } }
```

| Código | Caso |
| --- | --- |
| `200` | Lista o último respaldo |
| `400` | `tipo` distinto de `BASE` o `FOTOS` |
| `401` / `403` | Sin sesión / rol distinto de `ADMIN` |

## Restaurar fotos en una carpeta aislada

`scripts/restaurar_fotos.sh` verifica la huella **antes** de extraer y se niega a extraer en una
carpeta que no esté vacía:

```bash
RESPALDO_ARCHIVO=/respaldos/foundia_fotos_20261120T030000Z.tar.gz \
DESTINO=/tmp/fotos-restauradas \
scripts/restaurar_fotos.sh
```

No escribe nunca en el volumen `uploads_data` en uso; volver a ponerlas en producción es parte del
procedimiento de H58.

## Pruebas que se deben ejecutar y registrar

- Django: `tipo` en la lista, filtro por tipo, `ultimo/` sin tipo sigue siendo `BASE`,
  `ultimo/?tipo=FOTOS`, tipo inválido `400`, permisos.
- Docker con MySQL de QA: una ejecución deja dos filas `COMPLETADO` (`BASE` y `FOTOS`) con huella.
- La huella del `.tar.gz` coincide; con el archivo alterado la restauración se rechaza.
- Las fotos restauradas en una carpeta aislada son idénticas a las del volumen (mismos archivos y mismo SHA-256).
- El volumen de fotos está montado en solo lectura dentro del servicio de respaldo.
