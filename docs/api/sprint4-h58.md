# H58 — Recuperar los datos después de una falla

Responsable: Leonel. Web: Yajaira (procedimiento a la vista en el panel de respaldos, junto con H59 y H70).
Backlog 14.2. Usa los respaldos de la base (H38) y de fotos (H59).

H58 no agrega rutas a la API: es un **procedimiento** y un **script** (`scripts/recuperar_todo.sh`)
que recupera la base y las fotos y comprueba que coinciden.

## Qué hace `scripts/recuperar_todo.sh`

1. Elige el respaldo de la base y el de fotos **más recientes** de `RESPALDO_DIR`, o los indicados
   en `BASE_ARCHIVO` y `FOTOS_ARCHIVO`.
2. Se niega a usar un par separado por más de 1 hora (`DESFASE_MAXIMO_SEGUNDOS`), porque la base
   apuntaría a fotos que no son de ese momento. Se puede forzar con `RECUPERACION_ACEPTAR_DESFASE=si`.
3. Restaura la base con `restaurar_respaldo.sh` (H38): verifica la huella antes de tocar nada y se
   niega a escribir en `objetos_perdidos_db` sin `RESTAURACION_PERMITIR_PRODUCCION=si`.
4. Restaura las fotos con `restaurar_fotos.sh` (H59): verifica la huella y exige una carpeta vacía.
5. Comprueba conteos (usuarios, objetos, fotos, custodias) y que **cada foto registrada en la base
   tenga su archivo**. Las que faltan se listan como aviso (ver «Límites»).
6. Muestra el tiempo total.

## Procedimiento ante una pérdida de los volúmenes (Docker Compose)

> Requisito: el volumen `respaldos_data` sobrevivió (o se copió a otro lugar). Sin respaldos no hay
> recuperación posible.

1. **No ejecutar `docker compose down -v`** ni borrar `respaldos_data`.
2. Detener la API, el panel y el servicio de respaldo para que nadie escriba durante la recuperación:

   ```bash
   docker compose stop api admin-api respaldo frontend-usuario frontend-admin
   ```

3. Levantar MySQL. Si su volumen se perdió, MySQL crea uno nuevo y carga `script.sql` y `seed.sql`.
   **Esperar a que termine** antes de seguir:

   ```bash
   docker compose up -d mysql
   docker logs foundia-mysql 2>&1 | grep "MySQL init process done"
   ```

4. Si se perdió el volumen de fotos, `uploads_data` estará vacío (Docker lo crea al volver a usarlo).
5. Recuperar base y fotos con la imagen del servicio de respaldo, que ya tiene el cliente de MySQL.
   En Git Bash, anteponer `MSYS_NO_PATHCONV=1` para que no cambie las rutas del contenedor:

   ```bash
   docker compose build respaldo
   docker run --rm --network proyectofoundia_default --entrypoint bash \
     -v proyectofoundia_respaldos_data:/respaldos:ro \
     -v proyectofoundia_uploads_data:/destino-fotos \
     -v "$PWD/scripts:/s:ro" \
     -e DB_HOST=mysql -e DB_PASSWORD=root -e DB_NAME=objetos_perdidos_db \
     -e DESTINO_FOTOS=/destino-fotos \
     -e RESTAURACION_PERMITIR_PRODUCCION=si -e RESTAURACION_PERMITIR_SOBREESCRITURA=si \
     proyectofoundia-respaldo /s/recuperar_todo.sh
   ```

   Los nombres de red, volúmenes e imagen dependen del proyecto de Compose (por defecto, la carpeta
   `proyectofoundia`). Comprobarlos con `docker volume ls` y `docker network ls`.
6. Levantar el resto: `docker compose up -d`.
7. Comprobar: iniciar sesión, abrir una publicación con foto y el panel de custodias.

### Ensayo sin riesgo

Para practicar sin tocar producción, usar una base y una carpeta aisladas:
`DB_NAME=objetos_perdidos_restauracion`, `DESTINO_FOTOS=/tmp/fotos-restauradas` y sin las variables
`RESTAURACION_PERMITIR_*`.

## Verificación del 20/11/2026 (simulación real de la falla)

En un proyecto de Compose aislado (`foundia-qa`) con MySQL 8.0 y los datos de demostración:

| Paso | Resultado |
| --- | --- |
| Antes de la falla | 4 usuarios, 15 objetos, 8 fotos registradas, 2 custodias, 9 notificaciones; 5 archivos de fotos |
| Respaldo | `foundia_objetos_perdidos_db_20261120T020849Z.sql.gz` y `foundia_fotos_20261120T020849Z.tar.gz`, los dos `COMPLETADO` |
| Falla simulada | Se borraron los volúmenes de MySQL y de fotos. MySQL volvió con el `seed.sql` de fábrica (6 objetos, 0 custodias) y 0 fotos |
| Sin confirmar producción | `recuperar_todo.sh` se negó; la base y las fotos no cambiaron |
| Recuperación confirmada | **4 segundos**. Huellas `OK`; 4 usuarios, 15 objetos, 8 fotos, 2 custodias |
| Base antes vs. después | Idénticas: conteos y las 15 publicaciones con estado, código de custodia y foto |
| Fotos antes vs. después | Idénticas: 5 archivos con el mismo nombre y SHA-256 |
| La app después | Inicio de sesión, coincidencias, foto servida (`200`, JPEG), custodias `FND-2026-0001/0002` y etiqueta PDF |
| Par de distintos momentos | Rechazado (145 s de diferencia con un máximo de 60 s) sin crear la base |

## Límites (declarados)

- **Se pierde lo ocurrido después del último respaldo.** Con la frecuencia por defecto (24 h),
  hasta un día de publicaciones, reclamos y fotos.
- Solo se respaldan la base y las fotos. **No** se respaldan los archivos `.env`, las claves (IA,
  voz, Firebase) ni la configuración de Docker: hay que guardarlos aparte.
- Los respaldos viven en el volumen `respaldos_data` **del mismo equipo**. Si se pierde el disco
  completo, se pierden también. Copiarlos fuera del equipo es responsabilidad del equipo.
- Fotos registradas sin archivo: el script las informa como aviso. Como la base y las fotos se
  respaldan en la misma ejecución, una foto sin archivo ya faltaba en el origen. En los datos de
  ejemplo pasa con las 3 fotos de `seed.sql` (`/uploads/objetos/...`), que nunca tuvieron archivo.
- Las sesiones (JWT) siguen valiendo después de recuperar, salvo las revocadas después del respaldo:
  esas revocaciones se pierden. Si la falla fue un ataque, cerrar todas las sesiones después (H37).
- El tiempo medido (4 s) es con pocos datos. Con más publicaciones y fotos crece; volver a medir con
  datos reales antes de prometer un tiempo.
