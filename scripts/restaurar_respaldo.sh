#!/usr/bin/env bash
# =====================================================================
# H38 - Restauración de un respaldo lógico en una base AISLADA.
#
# Uso:
#   DB_NAME=objetos_perdidos_restauracion \
#   RESPALDO_ARCHIVO=/respaldos/foundia_objetos_perdidos_db_20261109T030000Z.sql.gz \
#   scripts/restaurar_respaldo.sh
#
# Guardas:
# - exige la huella .sha256 (o RESPALDO_SHA256) y la verifica antes de restaurar;
# - se niega a usar objetos_perdidos_db salvo RESTAURACION_PERMITIR_PRODUCCION=si;
# - si la base destino ya tiene tablas, exige RESTAURACION_PERMITIR_SOBREESCRITURA=si;
# - nunca monta ni modifica el volumen mysql_data.
# =====================================================================
set -Eeuo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-}"
RESPALDO_ARCHIVO="${RESPALDO_ARCHIVO:-}"
DB_PRODUCCION="${DB_PRODUCCION:-objetos_perdidos_db}"

log() {
    printf '%s %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fatal() {
    log "ERROR: $*" >&2
    exit 1
}

[[ -n "$DB_NAME" ]] || fatal "Falta DB_NAME con la base aislada de destino."
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fatal "DB_NAME inválido: $DB_NAME"
case "$DB_NAME" in
    mysql|information_schema|performance_schema|sys)
        fatal "DB_NAME apunta a una base del sistema: $DB_NAME" ;;
esac
if [[ "$DB_NAME" == "$DB_PRODUCCION" && "${RESTAURACION_PERMITIR_PRODUCCION:-no}" != "si" ]]; then
    fatal "Negado: $DB_NAME es la base de producción. Usa una base aislada o define RESTAURACION_PERMITIR_PRODUCCION=si."
fi
[[ -n "$RESPALDO_ARCHIVO" ]] || fatal "Falta RESPALDO_ARCHIVO con la ruta del .sql.gz."
[[ -f "$RESPALDO_ARCHIVO" ]] || fatal "No existe el archivo: $RESPALDO_ARCHIVO"

# 1. Verificar la huella antes de tocar cualquier base.
sha_file="${RESPALDO_ARCHIVO}.sha256"
if [[ -f "$sha_file" ]]; then
    log "Verificando huella con $sha_file"
    (cd "$(dirname "$RESPALDO_ARCHIVO")" && sha256sum -c "$(basename "$sha_file")") \
        || fatal "La huella SHA-256 no coincide. Restauración cancelada."
elif [[ -n "${RESPALDO_SHA256:-}" ]]; then
    log "Verificando huella con RESPALDO_SHA256"
    printf '%s  %s\n' "$RESPALDO_SHA256" "$RESPALDO_ARCHIVO" | sha256sum -c - \
        || fatal "La huella SHA-256 no coincide. Restauración cancelada."
else
    fatal "Falta $sha_file o RESPALDO_SHA256; no se restaura sin verificar la huella."
fi

CNF="$(mktemp)"
cleanup() { rm -f "$CNF"; }
trap cleanup EXIT

escapar_cnf() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

{
    printf '[client]\n'
    printf 'host=%s\n' "$DB_HOST"
    printf 'port=%s\n' "$DB_PORT"
    printf 'user=%s\n' "$DB_USERNAME"
    printf 'password="%s"\n' "$(escapar_cnf "$DB_PASSWORD")"
    printf 'default-character-set=utf8mb4\n'
} >"$CNF"
chmod 600 "$CNF"

mysql_cmd=(mysql --defaults-extra-file="$CNF" --connect-timeout=10 --batch --skip-column-names)

# 2. La base destino debe estar vacía, salvo confirmación explícita.
existentes="$("${mysql_cmd[@]}" -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
existentes="${existentes:-0}"
if (( existentes > 0 )); then
    if [[ "${RESTAURACION_PERMITIR_SOBREESCRITURA:-no}" != "si" ]]; then
        fatal "La base $DB_NAME ya tiene $existentes tablas. Define RESTAURACION_PERMITIR_SOBREESCRITURA=si para reemplazarla."
    fi
    log "Reemplazando la base aislada $DB_NAME ($existentes tablas)."
    "${mysql_cmd[@]}" -e "DROP DATABASE \`$DB_NAME\`;" \
        || fatal "No se pudo eliminar la base aislada $DB_NAME."
fi

log "Creando base aislada $DB_NAME"
"${mysql_cmd[@]}" -e \
    "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
    || fatal "No se pudo crear la base $DB_NAME."

# 3. Restaurar el volcado (sin CREATE DATABASE ni USE: se aplica sobre DB_NAME).
log "Restaurando $RESPALDO_ARCHIVO en $DB_NAME"
if ! gzip -dc "$RESPALDO_ARCHIVO" \
        | mysql --defaults-extra-file="$CNF" --connect-timeout=10 "$DB_NAME"; then
    fatal "La restauración falló; la base $DB_NAME quedó incompleta."
fi

tablas="$("${mysql_cmd[@]}" -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';")"
tablas="${tablas:-0}"
(( tablas > 0 )) || fatal "La restauración terminó sin tablas en $DB_NAME."

log "Restauración verificada: $tablas tablas en $DB_NAME."
"${mysql_cmd[@]}" -e \
    "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' ORDER BY table_name;"
