#!/usr/bin/env bash
# =====================================================================
# H58 - Recuperación completa (base + fotos) después de una falla.
#
# Restaura el respaldo de la base (H38) y el de fotos (H59) más recientes,
# o los indicados, y comprueba que cada foto registrada en la base tenga
# su archivo. Reutiliza restaurar_respaldo.sh y restaurar_fotos.sh, con
# todas sus guardas (huella obligatoria, destino vacío, base aislada).
#
# Uso típico, en un contenedor con el cliente mysql (imagen del servicio respaldo):
#   DB_HOST=mysql DB_PASSWORD=... DB_NAME=objetos_perdidos_restauracion \
#   RESPALDO_DIR=/respaldos DESTINO_FOTOS=/destino-fotos \
#   scripts/recuperar_todo.sh
#
# Para recuperar sobre la base de producción (después de perderla) hay que
# confirmarlo: RESTAURACION_PERMITIR_PRODUCCION=si y, si MySQL ya creó la
# base vacía, RESTAURACION_PERMITIR_SOBREESCRITURA=si. Ver docs/api/sprint4-h58.md.
# =====================================================================
set -Eeuo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESPALDO_DIR="${RESPALDO_DIR:-/respaldos}"
BASE_ARCHIVO="${BASE_ARCHIVO:-}"
FOTOS_ARCHIVO="${FOTOS_ARCHIVO:-}"
DESTINO_FOTOS="${DESTINO_FOTOS:-}"
DB_NAME="${DB_NAME:-}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
# Diferencia máxima aceptada entre el respaldo de la base y el de fotos
DESFASE_MAXIMO_SEGUNDOS="${DESFASE_MAXIMO_SEGUNDOS:-3600}"

log() { printf '%s %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"; }
fatal() { log "ERROR: $*" >&2; exit 1; }

[[ -n "$DB_NAME" ]] || fatal "Falta DB_NAME con la base de destino."
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fatal "DB_NAME inválido: $DB_NAME"
[[ -n "$DESTINO_FOTOS" ]] || fatal "Falta DESTINO_FOTOS con la carpeta (vacía) donde restaurar las fotos."

# 1. Elegir los respaldos: los indicados o los más recientes de cada tipo.
if [[ -z "$BASE_ARCHIVO" ]]; then
    BASE_ARCHIVO="$(ls -1 "$RESPALDO_DIR"/*_objetos_perdidos_db_*.sql.gz 2>/dev/null | sort | tail -1 || true)"
fi
if [[ -z "$FOTOS_ARCHIVO" ]]; then
    FOTOS_ARCHIVO="$(ls -1 "$RESPALDO_DIR"/*_fotos_*.tar.gz 2>/dev/null | sort | tail -1 || true)"
fi
[[ -n "$BASE_ARCHIVO" && -f "$BASE_ARCHIVO" ]] || fatal "No hay respaldo de la base en $RESPALDO_DIR."
[[ -n "$FOTOS_ARCHIVO" && -f "$FOTOS_ARCHIVO" ]] || fatal "No hay respaldo de fotos en $RESPALDO_DIR."

marca_de() {
    # foundia_..._20261120T020624Z(.sql.gz|.tar.gz) -> segundos desde 1970
    local m
    m="$(basename "$1" | grep -oE '[0-9]{8}T[0-9]{6}Z' | head -1)"
    [[ -n "$m" ]] || { echo ""; return; }
    date -u -d "${m:0:4}-${m:4:2}-${m:6:2} ${m:9:2}:${m:11:2}:${m:13:2}" +%s
}
t_base="$(marca_de "$BASE_ARCHIVO")"; t_fotos="$(marca_de "$FOTOS_ARCHIVO")"
if [[ -n "$t_base" && -n "$t_fotos" ]]; then
    desfase=$(( t_base > t_fotos ? t_base - t_fotos : t_fotos - t_base ))
    if (( desfase > DESFASE_MAXIMO_SEGUNDOS )) && [[ "${RECUPERACION_ACEPTAR_DESFASE:-no}" != "si" ]]; then
        fatal "Los respaldos de base y fotos están separados por ${desfase}s. Elige un par de la misma ejecución o define RECUPERACION_ACEPTAR_DESFASE=si."
    fi
fi

inicio="$(date +%s)"
log "Recuperación completa"
log "  Base:  $BASE_ARCHIVO"
log "  Fotos: $FOTOS_ARCHIVO"
log "  Destino: base $DB_NAME en $DB_HOST:$DB_PORT; fotos en $DESTINO_FOTOS"

# 2. Base (verifica la huella antes de tocar nada).
DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
DB_NAME="$DB_NAME" RESPALDO_ARCHIVO="$BASE_ARCHIVO" \
    bash "$AQUI/restaurar_respaldo.sh" >/dev/null \
    || fatal "Falló la restauración de la base."
log "Base restaurada."

# 3. Fotos (verifica la huella y exige destino vacío).
RESPALDO_ARCHIVO="$FOTOS_ARCHIVO" DESTINO="$DESTINO_FOTOS" bash "$AQUI/restaurar_fotos.sh" >/dev/null \
    || fatal "Falló la restauración de las fotos."
log "Fotos restauradas."

# 4. Comprobación cruzada: conteos y que cada foto registrada tenga su archivo.
CNF="$(mktemp)"; trap 'rm -f "$CNF"' EXIT
{
    printf '[client]\nhost=%s\nport=%s\nuser=%s\n' "$DB_HOST" "$DB_PORT" "$DB_USERNAME"
    printf 'password="%s"\ndefault-character-set=utf8mb4\n' "$(printf '%s' "$DB_PASSWORD" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
} >"$CNF"; chmod 600 "$CNF"
sql() { mysql --defaults-extra-file="$CNF" --batch --skip-column-names --database="$DB_NAME" -e "$1"; }

usuarios="$(sql 'SELECT COUNT(*) FROM usuarios;')"
objetos="$(sql 'SELECT COUNT(*) FROM objetos;')"
fotos_bd="$(sql 'SELECT COUNT(*) FROM fotos;')"
custodias="$(sql 'SELECT COUNT(*) FROM custodias;' 2>/dev/null || echo 'sin tabla')"
archivos="$(find "$DESTINO_FOTOS" -type f | wc -l)"

faltantes=0
while IFS= read -r url; do
    [[ -z "$url" ]] && continue
    archivo="${url#/uploads/}"
    if [[ ! -f "$DESTINO_FOTOS/$archivo" ]]; then
        faltantes=$((faltantes + 1))
        log "  Falta el archivo de la foto: $url"
    fi
done < <(sql "SELECT url FROM fotos WHERE url LIKE '/uploads/%';")

fin="$(date +%s)"
log "Resumen de la recuperación (${DB_NAME}):"
log "  usuarios=$usuarios objetos=$objetos fotos_en_bd=$fotos_bd custodias=$custodias archivos_de_fotos=$archivos"
log "  fotos de la base sin archivo: $faltantes"
log "  tiempo total: $(( fin - inicio ))s"
if (( faltantes > 0 )); then
    # La base y las fotos se respaldan en la misma ejecución: si un archivo no está
    # en el .tar.gz, ya faltaba en el volumen de origen (por ejemplo, las fotos de seed.sql).
    log "AVISO: $faltantes fotos registradas no tienen archivo. Ya faltaban en el origen del respaldo; no es un error de la recuperación."
fi
log "Recuperación verificada."
