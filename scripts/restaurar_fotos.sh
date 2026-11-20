#!/usr/bin/env bash
# =====================================================================
# H59 - Restauración de un respaldo de fotos en una carpeta AISLADA.
#
# Uso:
#   RESPALDO_ARCHIVO=/respaldos/foundia_fotos_20261120T030000Z.tar.gz \
#   DESTINO=/tmp/fotos-restauradas \
#   scripts/restaurar_fotos.sh
#
# Guardas:
# - exige la huella .sha256 (o RESPALDO_SHA256) y la verifica antes de extraer;
# - el destino debe estar vacío o no existir;
# - rechaza archivos con rutas absolutas o con «..» dentro del .tar.gz;
# - nunca escribe en el volumen uploads_data en uso.
# =====================================================================
set -Eeuo pipefail

RESPALDO_ARCHIVO="${RESPALDO_ARCHIVO:-}"
DESTINO="${DESTINO:-}"

log() {
    printf '%s %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fatal() {
    log "ERROR: $*" >&2
    exit 1
}

[[ -n "$RESPALDO_ARCHIVO" ]] || fatal "Falta RESPALDO_ARCHIVO con la ruta del .tar.gz."
[[ -f "$RESPALDO_ARCHIVO" ]] || fatal "No existe el archivo: $RESPALDO_ARCHIVO"
[[ -n "$DESTINO" ]] || fatal "Falta DESTINO con la carpeta aislada donde extraer."
case "$DESTINO" in
    /|/fotos|/fotos/|/app/uploads|/app/uploads/)
        fatal "Negado: $DESTINO es la carpeta de fotos en uso o la raíz. Usa una carpeta aislada." ;;
esac

# 1. Verificar la huella antes de extraer nada.
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

# 2. El contenido no puede escapar de DESTINO.
if tar -tzf "$RESPALDO_ARCHIVO" | grep -qE '(^/|(^|/)\.\.(/|$))'; then
    fatal "El respaldo contiene rutas absolutas o «..». Restauración cancelada."
fi

# 3. Destino vacío.
if [[ -e "$DESTINO" ]]; then
    [[ -d "$DESTINO" ]] || fatal "$DESTINO existe y no es una carpeta."
    [[ -z "$(ls -A "$DESTINO")" ]] || fatal "La carpeta $DESTINO no está vacía. Usa una carpeta aislada vacía."
else
    mkdir -p "$DESTINO"
fi

log "Extrayendo $RESPALDO_ARCHIVO en $DESTINO"
tar -xzf "$RESPALDO_ARCHIVO" -C "$DESTINO" --no-same-owner \
    || fatal "La extracción falló; $DESTINO puede estar incompleta."

archivos="$(find "$DESTINO" -type f | wc -l)"
log "Restauración verificada: $archivos archivos en $DESTINO."
