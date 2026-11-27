#!/usr/bin/env bash
# =====================================================================
# H70 - Restaurar una versión anterior (base + fotos de una misma ejecución).
#
# Sin VERSION: lista las versiones que hay en RESPALDO_DIR.
# Con VERSION=<marca UTC, p. ej. 20261120T020849Z>:
#   - ENSAYO (por defecto): la restaura en una base aislada y en una carpeta
#     temporal, verifica las huellas y muestra qué cambiaría frente a la base
#     actual. No toca la base ni las fotos en uso.
#   - APLICAR (RESTAURACION_APLICAR=si y RESTAURACION_RESPONSABLE=<nombre>):
#     hace antes un respaldo de seguridad del estado actual, reemplaza la base
#     y las fotos en uso, conserva el historial de respaldos y restauraciones
#     y las solicitudes de eliminación de cuenta (H34)
#     y cierra todas las sesiones.
#
# Corre en la imagen del servicio de respaldo. Ver docs/api/sprint4-h70.md.
# =====================================================================
set -Eeuo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESPALDO_DIR="${RESPALDO_DIR:-/respaldos}"
RESPALDO_PREFIJO="${RESPALDO_PREFIJO:-foundia}"
VERSION="${VERSION:-}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_PRODUCCION="${DB_PRODUCCION:-objetos_perdidos_db}"
DB_ENSAYO="${DB_ENSAYO:-objetos_perdidos_version}"
FOTOS_EN_USO="${FOTOS_EN_USO:-}"
RESTAURACION_APLICAR="${RESTAURACION_APLICAR:-no}"
RESTAURACION_RESPONSABLE="${RESTAURACION_RESPONSABLE:-}"
# H70: id de la restauración SOLICITADA desde el panel; si viene, se usa esa fila en vez de crear otra
RESTAURACION_ID="${RESTAURACION_ID:-}"
MANTENER_ENSAYO="${MANTENER_ENSAYO:-no}"
DESFASE_MAXIMO_SEGUNDOS="${DESFASE_MAXIMO_SEGUNDOS:-3600}"

TABLAS_COMPARADAS=(usuarios objetos fotos reclamos custodias conversaciones mensajes_chat)

log() { printf '%s %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"; }

REG_ID=""
TERMINADO=0
PASO="inicio"
PRODUCCION_TOCADA=0
SEGURIDAD=""
TMP_FOTOS=""
CNF="$(mktemp)"
TRABAJO="$(mktemp -d)"

MOTIVO=""
ENSAYO_CREADO=0

fatal() {
    MOTIVO="$*"
    log "ERROR: $*" >&2
    exit 1
}

al_salir() {
    local codigo=$?
    if (( codigo != 0 )); then
        if [[ -n "$REG_ID" && "$TERMINADO" != "1" ]]; then
            sql_prod "UPDATE restauraciones SET estado='FALLIDA', finalizado_en=UTC_TIMESTAMP(6),
                        mensaje='Falló en: $(limpiar "$PASO. $MOTIVO")' WHERE id=$REG_ID;" >/dev/null 2>&1 || true
        fi
        # La base aislada no sirve si el proceso no terminó; no se deja ocupando espacio.
        if (( ENSAYO_CREADO )) && [[ "$MANTENER_ENSAYO" != "si" ]]; then
            sql_prod "DROP DATABASE IF EXISTS \`$DB_ENSAYO\`;" >/dev/null 2>&1 || true
        fi
        if (( PRODUCCION_TOCADA )); then
            log "ATENCIÓN: la base o las fotos en uso pueden haber quedado incompletas." >&2
            log "Para volver al estado de antes, aplicar la versión de seguridad: VERSION=$SEGURIDAD" >&2
        fi
    fi
    [[ -n "$TMP_FOTOS" ]] && rm -rf "$TMP_FOTOS"
    rm -rf "$TRABAJO"
    rm -f "$CNF"
}
trap al_salir EXIT

limpiar() {
    # Para guardar en un VARCHAR sin romper la consulta: sin comillas, barras ni saltos de línea.
    # Los textos que se guardan son cortos por construcción: no se cortan (cortar podría partir una «ñ»).
    printf '%s' "$1" | tr '\n\r\t' '   ' | tr -d "\\\\'\"\`"
}

valido_db() {
    [[ "$1" =~ ^[A-Za-z0-9_]+$ ]] || fatal "Nombre de base inválido: $1"
    case "$1" in mysql|information_schema|performance_schema|sys) fatal "$1 es una base del sistema." ;; esac
}
valido_db "$DB_PRODUCCION"
valido_db "$DB_ENSAYO"
[[ "$DB_ENSAYO" != "$DB_PRODUCCION" ]] || fatal "DB_ENSAYO no puede ser la base de producción."
[[ "$DESFASE_MAXIMO_SEGUNDOS" =~ ^[0-9]+$ ]] || fatal "DESFASE_MAXIMO_SEGUNDOS debe ser un entero."

marca_de() {
    basename "$1" | grep -oE '[0-9]{8}T[0-9]{6}Z' | head -1 || true
}
segundos_de() {
    local m="$1"
    date -u -d "${m:0:4}-${m:4:2}-${m:6:2} ${m:9:2}:${m:11:2}:${m:13:2}" +%s
}

# Respaldo de fotos de la misma ejecución que el de la base (el más cercano, dentro del desfase).
par_fotos() {
    local t_base mejor="" mejor_d="" f m d
    t_base="$(segundos_de "$(marca_de "$1")")"
    for f in "$RESPALDO_DIR/${RESPALDO_PREFIJO}"_fotos_*.tar.gz; do
        [[ -f "$f" ]] || continue
        m="$(marca_de "$f")"; [[ -n "$m" ]] || continue
        d=$(( $(segundos_de "$m") - t_base )); (( d < 0 )) && d=$(( -d ))
        if (( d <= DESFASE_MAXIMO_SEGUNDOS )) && { [[ -z "$mejor_d" ]] || (( d < mejor_d )); }; then
            mejor="$f"; mejor_d="$d"
        fi
    done
    printf '%s' "$mejor"
}

bases_disponibles() {
    ls -1 "$RESPALDO_DIR/${RESPALDO_PREFIJO}_${DB_PRODUCCION}_"*.sql.gz 2>/dev/null | sort -r || true
}

# 1. Sin VERSION: listar y terminar (no necesita conectarse a MySQL).
if [[ -z "$VERSION" ]]; then
    log "Versiones en $RESPALDO_DIR (la más reciente primero):"
    hay=0
    while IFS= read -r base; do
        [[ -n "$base" ]] || continue
        hay=1
        fotos="$(par_fotos "$base")"
        printf '  %s  base=%s  fotos=%s\n' "$(marca_de "$base")" "$(basename "$base")" \
            "$( [[ -n "$fotos" ]] && basename "$fotos" || echo 'SIN PAR: no se puede restaurar')"
    done < <(bases_disponibles)
    (( hay )) || log "  No hay respaldos de la base."
    log "Para ensayar una: VERSION=<marca>. Para aplicarla: además RESTAURACION_APLICAR=si y RESTAURACION_RESPONSABLE=<nombre>."
    exit 0
fi

[[ "$VERSION" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || fatal "VERSION debe ser una marca como 20261120T020849Z."
APLICAR=0
[[ "$RESTAURACION_APLICAR" == "si" ]] && APLICAR=1
if (( APLICAR )); then
    (( $(printf '%s' "$RESTAURACION_RESPONSABLE" | wc -c) <= 120 )) || fatal "RESTAURACION_RESPONSABLE admite hasta 120 caracteres."
    RESPONSABLE="$(limpiar "$RESTAURACION_RESPONSABLE")"
    [[ -n "${RESPONSABLE// /}" ]] || fatal "Para aplicar hay que indicar RESTAURACION_RESPONSABLE con el nombre de quien restaura."
    [[ -n "$FOTOS_EN_USO" && -d "$FOTOS_EN_USO" ]] || fatal "Para aplicar hay que montar las fotos en uso y definir FOTOS_EN_USO."
    [[ "$FOTOS_EN_USO" != "/" ]] || fatal "FOTOS_EN_USO no puede ser la raíz."
fi

# 2. Buscar la versión y su par.
mapfile -t coincidencias < <(ls -1 "$RESPALDO_DIR/${RESPALDO_PREFIJO}_${DB_PRODUCCION}_${VERSION}"*.sql.gz 2>/dev/null || true)
(( ${#coincidencias[@]} == 1 )) || fatal "No hay un respaldo de la base con la versión $VERSION en $RESPALDO_DIR (encontrados: ${#coincidencias[@]})."
BASE_ARCHIVO="${coincidencias[0]}"
FOTOS_ARCHIVO="$(par_fotos "$BASE_ARCHIVO")"
[[ -n "$FOTOS_ARCHIVO" ]] || fatal "La versión $VERSION no tiene respaldo de fotos de la misma ejecución; no se restaura a medias."

{
    printf '[client]\nhost=%s\nport=%s\nuser=%s\n' "$DB_HOST" "$DB_PORT" "$DB_USERNAME"
    printf 'password="%s"\ndefault-character-set=utf8mb4\n' "$(printf '%s' "$DB_PASSWORD" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
} >"$CNF"
chmod 600 "$CNF"
sql_en() { local db="$1"; shift; mysql --defaults-extra-file="$CNF" --connect-timeout=10 --batch --skip-column-names --database="$db" -e "$1"; }
sql_prod() { sql_en "$DB_PRODUCCION" "$1"; }

existe_restauraciones="$(sql_prod "SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema='$DB_PRODUCCION' AND table_name='restauraciones';")" \
    || fatal "No se pudo conectar a MySQL en $DB_HOST:$DB_PORT."
[[ "$existe_restauraciones" == "1" ]] || fatal "Falta la tabla restauraciones: aplica database/migracion_h70_restauraciones.sql."

MODO="ENSAYO"; (( APLICAR )) && MODO="APLICADA"
if [[ -n "$RESTAURACION_ID" ]]; then
    [[ "$RESTAURACION_ID" =~ ^[0-9]+$ ]] || fatal "RESTAURACION_ID inválido."
    (( APLICAR )) || fatal "Una restauración pedida desde el panel siempre se aplica (RESTAURACION_APLICAR=si)."
    tomada="$(sql_prod "UPDATE restauraciones SET estado = 'EN_PROCESO', iniciado_en = UTC_TIMESTAMP(6),
                            respaldo_base = '$(basename "$BASE_ARCHIVO")', respaldo_fotos = '$(basename "$FOTOS_ARCHIVO")'
                         WHERE id = $RESTAURACION_ID AND estado = 'SOLICITADA' AND version = '$VERSION';
                        SELECT ROW_COUNT();")"
    [[ "$tomada" == "1" ]] || fatal "La restauración $RESTAURACION_ID no está SOLICITADA para la versión $VERSION."
    REG_ID="$RESTAURACION_ID"
else
    REG_ID="$(sql_prod "INSERT INTO restauraciones (version, modo, estado, respaldo_base, respaldo_fotos, responsable, iniciado_en)
        VALUES ('$VERSION', '$MODO', 'EN_PROCESO', '$(basename "$BASE_ARCHIVO")', '$(basename "$FOTOS_ARCHIVO")',
                $( (( APLICAR )) && printf "'%s'" "$RESPONSABLE" || printf 'NULL'), UTC_TIMESTAMP(6));
        SELECT LAST_INSERT_ID();")"
fi
[[ "$REG_ID" =~ ^[0-9]+$ ]] || fatal "No se pudo registrar la restauración."

inicio="$(date +%s)"
log "Versión $VERSION ($MODO)"
log "  Base:  $BASE_ARCHIVO"
log "  Fotos: $FOTOS_ARCHIVO"

# 3. Ensayo: base aislada y carpeta temporal (verifican la huella antes de tocar nada).
PASO="restaurar la versión en la base aislada $DB_ENSAYO"
ENSAYO_CREADO=1
DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
DB_NAME="$DB_ENSAYO" DB_PRODUCCION="$DB_PRODUCCION" RESPALDO_ARCHIVO="$BASE_ARCHIVO" \
RESTAURACION_PERMITIR_SOBREESCRITURA=si \
    bash "$AQUI/restaurar_respaldo.sh" >/dev/null || fatal "No se pudo restaurar la versión en la base aislada."

PASO="restaurar las fotos de la versión en una carpeta temporal"
TMP_FOTOS="$TRABAJO/fotos"
RESPALDO_ARCHIVO="$FOTOS_ARCHIVO" DESTINO="$TMP_FOTOS" bash "$AQUI/restaurar_fotos.sh" >/dev/null \
    || fatal "No se pudieron restaurar las fotos de la versión."

contar() { sql_en "$1" "SELECT COUNT(*) FROM \`$2\`;" 2>/dev/null || echo "sin_tabla"; }

PASO="comparar la versión con la base actual"
resumen=""
for tabla in "${TABLAS_COMPARADAS[@]}"; do
    actual="$(contar "$DB_PRODUCCION" "$tabla")"; version="$(contar "$DB_ENSAYO" "$tabla")"
    resumen+="$tabla $actual -> $version; "
done
archivos_version="$(find "$TMP_FOTOS" -type f | wc -l)"
if [[ -n "$FOTOS_EN_USO" && -d "$FOTOS_EN_USO" ]]; then
    archivos_actuales="$(find "$FOTOS_EN_USO" -type f ! -path '*/.restaurando-*' | wc -l)"
    resumen+="archivos de fotos $archivos_actuales -> $archivos_version; "
else
    resumen+="archivos de fotos en la versión $archivos_version; "
fi
max_id="$(sql_en "$DB_ENSAYO" "SELECT COALESCE(MAX(id), 0) FROM objetos;")"
nuevas="$(sql_prod "SELECT COUNT(*) FROM objetos WHERE id > $max_id;")"
resumen+="publicaciones posteriores a la versión que se perderían: $nuevas"

sin_archivo=0
while IFS= read -r url; do
    [[ -n "$url" ]] || continue
    [[ -f "$TMP_FOTOS/${url#/uploads/}" ]] || sin_archivo=$((sin_archivo + 1))
done < <(sql_en "$DB_ENSAYO" "SELECT url FROM fotos WHERE url LIKE '/uploads/%';")

log "Qué cambiaría (actual -> versión):"
printf '%s\n' "$resumen" | tr ';' '\n' | sed 's/^ */  /'
(( sin_archivo == 0 )) || log "AVISO: $sin_archivo fotos de la versión no tienen archivo (ya faltaban cuando se respaldó)."

# 4. Mismo esquema: una versión de antes de una migración rompería la API actual.
PASO="comparar el esquema"
esquema() {
    sql_en "$1" "SELECT table_name, column_name, column_type, is_nullable FROM information_schema.columns
                 WHERE table_schema='$1' ORDER BY table_name, column_name;"
}
# La imagen de respaldo no trae diffutils: se compara con sort y comm (coreutils).
esquema "$DB_PRODUCCION" | LC_ALL=C sort >"$TRABAJO/esquema_actual"
esquema "$DB_ENSAYO" | LC_ALL=C sort >"$TRABAJO/esquema_version"
mismo_esquema=1
if [[ "$(sha256sum <"$TRABAJO/esquema_actual")" != "$(sha256sum <"$TRABAJO/esquema_version")" ]]; then
    mismo_esquema=0
    log "La versión $VERSION tiene un esquema distinto del actual (- solo en la actual, + solo en la versión):"
    LC_ALL=C comm -23 "$TRABAJO/esquema_actual" "$TRABAJO/esquema_version" | head -10 | sed 's/^/  - /' || true
    LC_ALL=C comm -13 "$TRABAJO/esquema_actual" "$TRABAJO/esquema_version" | head -10 | sed 's/^/  + /' || true
fi

if (( ! APLICAR )); then
    (( mismo_esquema )) && aviso="Se puede aplicar." || aviso="No se puede aplicar: esquema distinto."
    if [[ "$MANTENER_ENSAYO" == "si" ]]; then
        log "La base aislada $DB_ENSAYO queda para revisarla."
    else
        sql_en "$DB_PRODUCCION" "DROP DATABASE \`$DB_ENSAYO\`;" >/dev/null
    fi
    sql_prod "UPDATE restauraciones SET estado='COMPLETADA', finalizado_en=UTC_TIMESTAMP(6),
                mensaje='$(limpiar "$resumen. $aviso")' WHERE id=$REG_ID;"
    TERMINADO=1
    log "Ensayo terminado en $(( $(date +%s) - inicio ))s. La base y las fotos en uso no cambiaron. $aviso"
    (( mismo_esquema )) && log "Para aplicarla: VERSION=$VERSION RESTAURACION_APLICAR=si RESTAURACION_RESPONSABLE=<nombre> (ver docs/api/sprint4-h70.md)."
    exit 0
fi

(( mismo_esquema )) || fatal "No se aplica: la versión $VERSION tiene un esquema distinto del actual. La base en uso no cambió."

# 5a. Respaldo de seguridad del estado actual (queda como una versión más, para deshacer).
PASO="hacer el respaldo de seguridad"
antes="$(bases_disponibles | head -1)"
sleep 1  # la marca de la versión de seguridad no puede coincidir con la última
RESPALDO_UNA_VEZ=1 RESPALDO_RETENCION_DIAS=0 RESPALDO_FOTOS=1 \
DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
DB_NAME="$DB_PRODUCCION" RESPALDO_DIR="$RESPALDO_DIR" RESPALDO_PREFIJO="$RESPALDO_PREFIJO" FOTOS_DIR="$FOTOS_EN_USO" \
    bash "$AQUI/respaldo.sh" >"$TRABAJO/respaldo.log" 2>&1 || { cat "$TRABAJO/respaldo.log" >&2; fatal "El respaldo de seguridad falló. La base en uso no cambió."; }
despues="$(bases_disponibles | head -1)"
[[ -n "$despues" && "$despues" != "$antes" ]] \
    || { cat "$TRABAJO/respaldo.log" >&2; fatal "No se creó el respaldo de seguridad (¿hay otro respaldo en curso?). La base en uso no cambió."; }
SEGURIDAD="$(marca_de "$despues")"
[[ -n "$(par_fotos "$despues")" ]] || fatal "El respaldo de seguridad no tiene su par de fotos. La base en uso no cambió."
sql_prod "UPDATE restauraciones SET respaldo_seguridad='$SEGURIDAD' WHERE id=$REG_ID;"
log "Respaldo de seguridad: versión $SEGURIDAD"

# 5b. Lo que se conserva: historial de respaldos y restauraciones, las solicitudes de eliminación (H34: una
# versión vieja no debe revivir una cuenta ya eliminada) y la versión de sesión de cada cuenta.
PASO="guardar el historial y las sesiones"
opciones_dump=(--single-transaction --skip-lock-tables --no-create-info --skip-triggers --default-character-set=utf8mb4)
mysqldump --help 2>/dev/null | grep -q -- '--set-gtid-purged' && opciones_dump+=(--set-gtid-purged=OFF)
mysqldump --help 2>/dev/null | grep -q -- '--no-tablespaces' && opciones_dump+=(--no-tablespaces)
mysqldump --defaults-extra-file="$CNF" "${opciones_dump[@]}" "$DB_PRODUCCION" respaldos restauraciones solicitudes_eliminacion >"$TRABAJO/historial.sql"
sql_prod "SELECT id, sesion_version FROM usuarios;" >"$TRABAJO/sesiones.tsv"
valores=""
while IFS=$'\t' read -r id ver; do
    [[ "$id" =~ ^[0-9]+$ && "$ver" =~ ^[0-9]+$ ]] || continue
    valores+="${valores:+,}($id,$ver)"
done <"$TRABAJO/sesiones.tsv"

# 5c. Reemplazar la base en uso.
PASO="reemplazar la base en uso"
PRODUCCION_TOCADA=1
DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
DB_NAME="$DB_PRODUCCION" DB_PRODUCCION="$DB_PRODUCCION" RESPALDO_ARCHIVO="$BASE_ARCHIVO" \
RESTAURACION_PERMITIR_PRODUCCION=si RESTAURACION_PERMITIR_SOBREESCRITURA=si \
    bash "$AQUI/restaurar_respaldo.sh" >/dev/null || fatal "Falló el reemplazo de la base en uso."

PASO="devolver el historial de respaldos, restauraciones y solicitudes de eliminación"
sql_prod "SET FOREIGN_KEY_CHECKS=0; DELETE FROM restauraciones; DELETE FROM respaldos; DELETE FROM solicitudes_eliminacion; SET FOREIGN_KEY_CHECKS=1;"
mysql --defaults-extra-file="$CNF" --database="$DB_PRODUCCION" <"$TRABAJO/historial.sql"

PASO="cerrar todas las sesiones"
sql_prod "CREATE TEMPORARY TABLE sesiones_previas (id BIGINT PRIMARY KEY, v INT NOT NULL);
          $( [[ -n "$valores" ]] && printf 'INSERT INTO sesiones_previas VALUES %s;' "$valores" )
          UPDATE usuarios u LEFT JOIN sesiones_previas p ON p.id = u.id
             SET u.sesion_version = GREATEST(u.sesion_version, COALESCE(p.v, 0)) + 1;"

# 5d. Reemplazar las fotos en uso (se extraen al lado y se cambian al final).
PASO="reemplazar las fotos en uso"
nueva="$FOTOS_EN_USO/.restaurando-$VERSION"
rm -rf "$nueva"
RESPALDO_ARCHIVO="$FOTOS_ARCHIVO" DESTINO="$nueva" bash "$AQUI/restaurar_fotos.sh" >/dev/null \
    || fatal "Falló la extracción de las fotos de la versión."
find "$FOTOS_EN_USO" -mindepth 1 -maxdepth 1 ! -name ".restaurando-$VERSION" -exec rm -rf {} +
find "$nueva" -mindepth 1 -maxdepth 1 -exec mv -t "$FOTOS_EN_USO" {} +
rmdir "$nueva"

# 6. Comprobar que la base y las fotos en uso son las de la versión.
PASO="verificar la restauración"
for tabla in "${TABLAS_COMPARADAS[@]}"; do
    [[ "$(contar "$DB_PRODUCCION" "$tabla")" == "$(contar "$DB_ENSAYO" "$tabla")" ]] \
        || fatal "Después de restaurar, $tabla no coincide con la versión."
done
[[ "$(find "$FOTOS_EN_USO" -type f | wc -l)" == "$archivos_version" ]] \
    || fatal "Después de restaurar, los archivos de fotos no coinciden con la versión."

[[ "$MANTENER_ENSAYO" == "si" ]] || sql_prod "DROP DATABASE \`$DB_ENSAYO\`;" >/dev/null
sql_prod "UPDATE restauraciones SET estado='COMPLETADA', finalizado_en=UTC_TIMESTAMP(6),
            mensaje='$(limpiar "$resumen. Sesiones cerradas.")' WHERE id=$REG_ID;"
TERMINADO=1
PRODUCCION_TOCADA=0
log "Versión $VERSION aplicada en $(( $(date +%s) - inicio ))s. Se cerraron todas las sesiones."
log "Para deshacerla: VERSION=$SEGURIDAD RESTAURACION_APLICAR=si RESTAURACION_RESPONSABLE=<nombre>."
# H34: la versión restaurada puede traer de vuelta cuentas que ya se eliminaron
revividas="$(sql_prod "SELECT COUNT(*) FROM solicitudes_eliminacion s JOIN usuarios u ON u.id = s.usuario_id
                        WHERE s.estado = 'ATENDIDA' AND u.estado <> 'ELIMINADO';" 2>/dev/null || echo 0)"
if [[ "$revividas" =~ ^[0-9]+$ ]] && (( revividas > 0 )); then
    log "IMPORTANTE (H34): la versión trajo de vuelta $revividas cuenta(s) que ya se habían eliminado."
    log "  Ejecuta en el panel: docker compose exec admin-api python manage.py reaplicar_eliminaciones"
fi
