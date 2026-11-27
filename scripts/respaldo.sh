#!/usr/bin/env bash
# =====================================================================
# H38 - Respaldo lógico de MySQL con huella SHA-256 y registro en `respaldos`.
#
# Uso programado: servicio `respaldo` de docker-compose.yml.
# Uso manual:     docker compose run --rm -e RESPALDO_UNA_VEZ=1 respaldo
#
# El respaldo se escribe en RESPALDO_DIR (volumen propio). Nunca monta ni
# borra el volumen mysql_data. Cada ejecución deja una fila en `respaldos`
# con estado EN_PROCESO, COMPLETADO o FALLIDO.
#
# H59 - En la misma ejecución comprime FOTOS_DIR (volumen uploads_data,
# montado en solo lectura) en un .tar.gz con su huella, registrado con
# tipo FOTOS. RESPALDO_FOTOS=0 lo desactiva.
# =====================================================================
set -Eeuo pipefail

DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-objetos_perdidos_db}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

RESPALDO_DIR="${RESPALDO_DIR:-/respaldos}"
RESPALDO_INTERVALO_SEGUNDOS="${RESPALDO_INTERVALO_SEGUNDOS:-86400}"
RESPALDO_RETENCION_DIAS="${RESPALDO_RETENCION_DIAS:-7}"
RESPALDO_UNA_VEZ="${RESPALDO_UNA_VEZ:-0}"
RESPALDO_PREFIJO="${RESPALDO_PREFIJO:-foundia}"
RESPALDO_FOTOS="${RESPALDO_FOTOS:-1}"
FOTOS_DIR="${FOTOS_DIR:-/fotos}"
# Un candado más viejo que esto lo dejó un proceso que ya no existe (ningún respaldo dura tanto).
RESPALDO_CANDADO_MAXIMO_SEGUNDOS="${RESPALDO_CANDADO_MAXIMO_SEGUNDOS:-7200}"
# Si el candado está tomado por un respaldo en curso (o por uno abandonado todavía reciente), se vuelve a
# intentar en pocos minutos y no hasta el próximo intervalo: un apagado forzado no debe costar un día de respaldo.
RESPALDO_REINTENTO_CANDADO_SEGUNDOS="${RESPALDO_REINTENTO_CANDADO_SEGUNDOS:-300}"
# H70: cada cuántos segundos se revisa si el panel pidió restaurar una versión (0 = no se revisa)
RESTAURACION_REVISAR_SEGUNDOS="${RESTAURACION_REVISAR_SEGUNDOS:-15}"
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() {
    printf '%s %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fatal() {
    log "ERROR: $*" >&2
    exit 1
}

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fatal "DB_NAME inválido: $DB_NAME"
[[ "$RESPALDO_INTERVALO_SEGUNDOS" =~ ^[0-9]+$ ]] || fatal "RESPALDO_INTERVALO_SEGUNDOS debe ser un entero"
[[ "$RESPALDO_RETENCION_DIAS" =~ ^[0-9]+$ ]] || fatal "RESPALDO_RETENCION_DIAS debe ser un entero"
[[ "$RESPALDO_CANDADO_MAXIMO_SEGUNDOS" =~ ^[0-9]+$ ]] || fatal "RESPALDO_CANDADO_MAXIMO_SEGUNDOS debe ser un entero"
[[ "$RESPALDO_REINTENTO_CANDADO_SEGUNDOS" =~ ^[0-9]+$ ]] || fatal "RESPALDO_REINTENTO_CANDADO_SEGUNDOS debe ser un entero"
[[ "$RESTAURACION_REVISAR_SEGUNDOS" =~ ^[0-9]+$ ]] || fatal "RESTAURACION_REVISAR_SEGUNDOS debe ser un entero"

mkdir -p "$RESPALDO_DIR"

# Evita dos respaldos simultáneos sin depender de flock. El candado se toma solo mientras se
# respalda (no durante la espera de 24 h) y se suelta también cuando Docker detiene el servicio.
LOCK_DIR="$RESPALDO_DIR/.respaldo.lock"
CANDADO_PROPIO=0

tomar_candado() {
    local desde edad
    if mkdir "$LOCK_DIR" 2>/dev/null; then
        CANDADO_PROPIO=1
        date +%s >"$LOCK_DIR/desde"
        return 0
    fi
    # ¿Lo dejó un proceso que murió sin soltarlo (por ejemplo, un apagado forzado)?
    desde="$(cat "$LOCK_DIR/desde" 2>/dev/null || stat -c %Y "$LOCK_DIR" 2>/dev/null || echo 0)"
    [[ "$desde" =~ ^[0-9]+$ ]] || desde=0
    edad=$(( $(date +%s) - desde ))
    if (( edad > RESPALDO_CANDADO_MAXIMO_SEGUNDOS )); then
        log "Se libera un candado abandonado hace ${edad}s (máximo ${RESPALDO_CANDADO_MAXIMO_SEGUNDOS}s)."
        rm -rf "$LOCK_DIR"
        if mkdir "$LOCK_DIR" 2>/dev/null; then
            CANDADO_PROPIO=1
            date +%s >"$LOCK_DIR/desde"
            return 0
        fi
    fi
    return 1
}

soltar_candado() {
    if (( CANDADO_PROPIO )); then
        rm -rf "$LOCK_DIR"
        CANDADO_PROPIO=0
    fi
}

# Archivo de credenciales temporal: la contraseña no aparece en la línea de comandos.
CNF="$(mktemp)"
cleanup() {
    rm -f "$CNF"
    soltar_candado
}
trap cleanup EXIT
# El script es el proceso principal del contenedor: sin esto, «docker stop» lo mata a los 10 s
# sin pasar por cleanup y el candado queda para siempre.
trap 'log "Señal de apagado recibida: se suelta el candado y se termina."; exit 143' TERM INT

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

mysql_cmd=(mysql --defaults-extra-file="$CNF" --connect-timeout=10 --batch --skip-column-names --database="$DB_NAME")

sanitizar_mensaje() {
    # Quita saltos de línea y barras invertidas, duplica comillas y limita a 500.
    printf '%s' "$1" | tr '\n\r\t' '   ' | tr -d '\\' | sed "s/'/''/g" | cut -c1-500
}

# H59: la columna `tipo` existe solo si se aplicó migracion_h59_respaldo_fotos.sql.
tiene_columna_tipo() {
    local n
    n="$("${mysql_cmd[@]}" -e \
        "SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema='$DB_NAME' AND table_name='respaldos' AND column_name='tipo';")" || return 1
    [[ "$n" == "1" ]]
}

registrar_inicio() {
    local nombre="$1" tipo="${2:-BASE}"
    if [[ "$CON_TIPO" == "1" ]]; then
        "${mysql_cmd[@]}" -e \
            "INSERT INTO respaldos (tipo, nombre_archivo, estado, iniciado_en)
             VALUES ('$tipo', '$nombre', 'EN_PROCESO', UTC_TIMESTAMP(6));"
    else
        "${mysql_cmd[@]}" -e \
            "INSERT INTO respaldos (nombre_archivo, estado, iniciado_en)
             VALUES ('$nombre', 'EN_PROCESO', UTC_TIMESTAMP(6));"
    fi
}

registrar_resultado() {
    local nombre="$1" estado="$2" huella="$3" tamano="$4" mensaje="$5"
    local mensaje_sql
    mensaje_sql="$(sanitizar_mensaje "$mensaje")"
    "${mysql_cmd[@]}" -e \
        "UPDATE respaldos
            SET estado='$estado',
                huella_sha256=$([ -n "$huella" ] && printf "'%s'" "$huella" || printf 'NULL'),
                tamano_bytes=$([ -n "$tamano" ] && printf '%s' "$tamano" || printf 'NULL'),
                finalizado_en=UTC_TIMESTAMP(6),
                mensaje=$([ -n "$mensaje_sql" ] && printf "'%s'" "$mensaje_sql" || printf 'NULL')
          WHERE nombre_archivo='$nombre';"
}

opciones_dump=(
    --single-transaction
    --quick
    --skip-lock-tables
    --routines
    --triggers
    --events
    --default-character-set=utf8mb4
)
# Compatibilidad con clientes MySQL 8 y MariaDB.
if mysqldump --help 2>/dev/null | grep -q -- '--set-gtid-purged'; then
    opciones_dump+=(--set-gtid-purged=OFF)
fi
if mysqldump --help 2>/dev/null | grep -q -- '--no-tablespaces'; then
    opciones_dump+=(--no-tablespaces)
fi

ejecutar_respaldo() {
    local marca nombre temp final sha tamano
    marca="$(date -u +%Y%m%dT%H%M%SZ)"
    nombre="${RESPALDO_PREFIJO}_${DB_NAME}_${marca}.sql.gz"
    if [[ -e "$RESPALDO_DIR/$nombre" ]]; then
        nombre="${RESPALDO_PREFIJO}_${DB_NAME}_${marca}_$$.sql.gz"
    fi
    temp="$RESPALDO_DIR/.$nombre.tmp"
    final="$RESPALDO_DIR/$nombre"

    log "Iniciando respaldo $nombre"
    if ! registrar_inicio "$nombre"; then
        log "No se pudo registrar el respaldo en la tabla respaldos."
        return 1
    fi

    if ! mysqldump --defaults-extra-file="$CNF" "${opciones_dump[@]}" "$DB_NAME" \
            | gzip -9 >"$temp"; then
        rm -f "$temp"
        registrar_resultado "$nombre" "FALLIDO" "" "" "mysqldump o gzip fallaron" || true
        log "El respaldo $nombre falló."
        return 1
    fi

    # Huella sobre el archivo comprimido; se verifica antes de publicarlo.
    sha="$(sha256sum "$temp" | awk '{print $1}')"
    if [[ ! "$sha" =~ ^[0-9a-f]{64}$ ]]; then
        rm -f "$temp"
        registrar_resultado "$nombre" "FALLIDO" "" "" "no se pudo calcular la huella SHA-256" || true
        log "No se pudo calcular la huella de $nombre."
        return 1
    fi
    printf '%s  %s\n' "$sha" "$nombre" >"$temp.sha256"
    mv "$temp" "$final"
    mv "$temp.sha256" "$final.sha256"

    if ! (cd "$RESPALDO_DIR" && sha256sum -c "$nombre.sha256" >/dev/null 2>&1); then
        registrar_resultado "$nombre" "FALLIDO" "$sha" "" "la huella no coincide con el archivo" || true
        log "La verificación de huella de $nombre falló."
        return 1
    fi

    tamano="$(stat -c %s "$final")"
    if ! registrar_resultado "$nombre" "COMPLETADO" "$sha" "$tamano" ""; then
        log "El archivo $nombre existe, pero no se pudo actualizar su registro."
        return 1
    fi

    log "Respaldo completado: $final ($tamano bytes, sha256=$sha)"
    return 0
}

# H59: comprime el volumen de fotos (solo lectura) con su huella SHA-256.
ejecutar_respaldo_fotos() {
    local marca nombre temp final sha tamano archivos
    [[ "$RESPALDO_FOTOS" == "1" ]] || { log "Respaldo de fotos desactivado (RESPALDO_FOTOS=$RESPALDO_FOTOS)."; return 0; }
    if [[ "$CON_TIPO" != "1" ]]; then
        log "Se omite el respaldo de fotos: falta la columna respaldos.tipo (aplica migracion_h59_respaldo_fotos.sql)."
        return 0
    fi

    marca="$(date -u +%Y%m%dT%H%M%SZ)"
    nombre="${RESPALDO_PREFIJO}_fotos_${marca}.tar.gz"
    if [[ -e "$RESPALDO_DIR/$nombre" ]]; then
        nombre="${RESPALDO_PREFIJO}_fotos_${marca}_$$.tar.gz"
    fi
    temp="$RESPALDO_DIR/.$nombre.tmp"
    final="$RESPALDO_DIR/$nombre"

    log "Iniciando respaldo de fotos $nombre desde $FOTOS_DIR"
    if ! registrar_inicio "$nombre" "FOTOS"; then
        log "No se pudo registrar el respaldo de fotos en la tabla respaldos."
        return 1
    fi

    if [[ ! -d "$FOTOS_DIR" ]]; then
        registrar_resultado "$nombre" "FALLIDO" "" "" "no existe la carpeta de fotos $FOTOS_DIR" || true
        log "No existe la carpeta de fotos $FOTOS_DIR."
        return 1
    fi

    archivos="$(find "$FOTOS_DIR" -type f | wc -l)"
    if ! tar -czf "$temp" -C "$FOTOS_DIR" .; then
        rm -f "$temp"
        registrar_resultado "$nombre" "FALLIDO" "" "" "tar o gzip fallaron al comprimir las fotos" || true
        log "El respaldo de fotos $nombre falló."
        return 1
    fi

    sha="$(sha256sum "$temp" | awk '{print $1}')"
    if [[ ! "$sha" =~ ^[0-9a-f]{64}$ ]]; then
        rm -f "$temp"
        registrar_resultado "$nombre" "FALLIDO" "" "" "no se pudo calcular la huella SHA-256" || true
        return 1
    fi
    printf '%s  %s\n' "$sha" "$nombre" >"$temp.sha256"
    mv "$temp" "$final"
    mv "$temp.sha256" "$final.sha256"

    if ! (cd "$RESPALDO_DIR" && sha256sum -c "$nombre.sha256" >/dev/null 2>&1); then
        registrar_resultado "$nombre" "FALLIDO" "$sha" "" "la huella no coincide con el archivo" || true
        return 1
    fi

    tamano="$(stat -c %s "$final")"
    if ! registrar_resultado "$nombre" "COMPLETADO" "$sha" "$tamano" "$archivos archivos"; then
        log "El archivo $nombre existe, pero no se pudo actualizar su registro."
        return 1
    fi
    log "Respaldo de fotos completado: $final ($archivos archivos, $tamano bytes, sha256=$sha)"
    return 0
}

# H70: la columna `eliminado_en` existe solo si se aplicó migracion_h70_restauraciones.sql.
tiene_columna_eliminado() {
    local n
    n="$("${mysql_cmd[@]}" -e \
        "SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema='$DB_NAME' AND table_name='respaldos' AND column_name='eliminado_en';")" || return 1
    [[ "$n" == "1" ]]
}

aplicar_retencion() {
    (( RESPALDO_RETENCION_DIAS > 0 )) || return 0
    local borrados archivo nombre
    # Solo borra archivos del volumen de respaldos; las filas quedan como historial.
    borrados="$(find "$RESPALDO_DIR" -maxdepth 1 -type f \
        \( -name "${RESPALDO_PREFIJO}_*.sql.gz" -o -name "${RESPALDO_PREFIJO}_*.sql.gz.sha256" \
           -o -name "${RESPALDO_PREFIJO}_fotos_*.tar.gz" -o -name "${RESPALDO_PREFIJO}_fotos_*.tar.gz.sha256" \) \
        -mtime +"$RESPALDO_RETENCION_DIAS" -print -delete)" || true
    [[ -n "$borrados" ]] || return 0
    while IFS= read -r archivo; do log "Retención: borrado $archivo"; done <<<"$borrados"
    # H70: la fila queda, pero marcada, para que el panel no la ofrezca como versión disponible.
    tiene_columna_eliminado || return 0
    while IFS= read -r archivo; do
        nombre="$(basename "$archivo")"
        [[ "$nombre" == *.sha256 ]] && continue
        [[ "$nombre" =~ ^[A-Za-z0-9_.-]+$ ]] || continue
        "${mysql_cmd[@]}" -e \
            "UPDATE respaldos SET eliminado_en=UTC_TIMESTAMP(6)
              WHERE nombre_archivo='$nombre' AND eliminado_en IS NULL;" \
            || log "No se pudo marcar $nombre como eliminado."
    done <<<"$borrados"
}

# H70: restauración pedida desde el panel. La ejecuta restaurar_version.sh con todas sus guardas
# (huellas, mismo esquema, respaldo de seguridad previo). Pase lo que pase, la solicitud no queda abierta:
# si quedara SOLICITADA o EN_PROCESO, las APIs seguirían en modo mantenimiento.
atender_restauracion() {
    local fila id version responsable
    fila="$("${mysql_cmd[@]}" -e "SELECT id, version, COALESCE(responsable, 'Panel') FROM restauraciones
                                  WHERE modo = 'APLICADA' AND estado = 'SOLICITADA' ORDER BY id LIMIT 1;" 2>/dev/null)" || return 0
    [[ -n "$fila" ]] || return 0
    IFS=$'\t' read -r id version responsable <<<"$fila"
    if [[ ! "$id" =~ ^[0-9]+$ || ! "$version" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
        log "Solicitud de restauración con datos inválidos; se ignora."
        return 0
    fi
    log "Restauración pedida desde el panel: versión $version (solicitud $id, $responsable)."
    local salida motivo
    salida="$(mktemp)"
    if RESTAURACION_ID="$id" VERSION="$version" RESTAURACION_APLICAR=si RESTAURACION_RESPONSABLE="$responsable" \
       FOTOS_EN_USO="$FOTOS_DIR" DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_USERNAME="$DB_USERNAME" \
       DB_PASSWORD="$DB_PASSWORD" DB_PRODUCCION="$DB_NAME" RESPALDO_DIR="$RESPALDO_DIR" RESPALDO_PREFIJO="$RESPALDO_PREFIJO" \
       bash "$AQUI/restaurar_version.sh" >"$salida" 2>&1; then
        cat "$salida"
        log "Restauración $id terminada."
    else
        cat "$salida"
        log "La restauración $id no se completó."
    fi
    # El motivo que dio el script, para que el panel lo muestre (sin comillas ni saltos de línea)
    motivo="$(grep -o 'ERROR: .*' "$salida" | tail -1 | cut -c8- || true)"
    motivo="$(sanitizar_mensaje "${motivo:-revisar el registro del servicio de respaldo}")"
    rm -f "$salida"
    "${mysql_cmd[@]}" -e "UPDATE restauraciones SET estado = 'FALLIDA', finalizado_en = UTC_TIMESTAMP(6),
                            mensaje = 'No se completó: $motivo La base y las fotos en uso no cambiaron.'
                          WHERE id = $id AND estado IN ('SOLICITADA', 'EN_PROCESO');" 2>/dev/null || true
}

# Espera entre respaldos sin dejar de revisar si el panel pidió una restauración
esperar() {
    local restante="$1" paso
    while (( restante > 0 )); do
        paso=$restante
        if (( RESTAURACION_REVISAR_SEGUNDOS > 0 && RESTAURACION_REVISAR_SEGUNDOS < paso )); then
            paso=$RESTAURACION_REVISAR_SEGUNDOS
        fi
        # En segundo plano y con wait: así la señal de apagado se atiende al instante
        sleep "$paso" &
        wait $!
        restante=$(( restante - paso ))
        if (( RESTAURACION_REVISAR_SEGUNDOS > 0 )); then
            atender_restauracion || true
        fi
    done
}

while true; do
    if tomar_candado; then
        CON_TIPO=0
        if tiene_columna_tipo; then CON_TIPO=1; fi

        ok_base=1; ok_fotos=1
        ejecutar_respaldo || ok_base=0
        # Si la base falla, igual se intenta el respaldo de fotos (y al revés).
        ejecutar_respaldo_fotos || ok_fotos=0

        if (( ok_base && ok_fotos )); then
            aplicar_retencion
        elif [[ "$RESPALDO_UNA_VEZ" == "1" ]]; then
            exit 1
        else
            log "El respaldo falló (base=$ok_base, fotos=$ok_fotos); se reintentará en ${RESPALDO_INTERVALO_SEGUNDOS}s."
        fi
        soltar_candado
    else
        [[ "$RESPALDO_UNA_VEZ" == "1" ]] && { log "Ya hay un respaldo en curso; se omite esta vuelta."; exit 0; }
        log "Ya hay un respaldo en curso; se vuelve a intentar en ${RESPALDO_REINTENTO_CANDADO_SEGUNDOS}s."
        esperar "$RESPALDO_REINTENTO_CANDADO_SEGUNDOS"
        continue
    fi

    [[ "$RESPALDO_UNA_VEZ" == "1" ]] && break

    log "Próximo respaldo en ${RESPALDO_INTERVALO_SEGUNDOS}s."
    esperar "$RESPALDO_INTERVALO_SEGUNDOS"
done
