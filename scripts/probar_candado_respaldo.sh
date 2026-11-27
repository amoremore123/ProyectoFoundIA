#!/bin/bash
# Prueba del candado del respaldo: con un candado reciente tomado, el servicio debe reintentar en segundos
# (RESPALDO_REINTENTO_CANDADO_SEGUNDOS) y no esperar un día entero. Es una prueba de la lógica, no hace respaldos
# reales: sale 0 si vio el reintento corto y que siguió cuando se soltó el candado.
set -u
AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
D="$(mktemp -d)"
trap 'rm -rf "$D"' EXIT
mkdir -p "$D/.respaldo.lock"
date +%s >"$D/.respaldo.lock/desde"
( sleep 5; rm -rf "$D/.respaldo.lock" ) &
salida="$(RESPALDO_DIR="$D" RESPALDO_REINTENTO_CANDADO_SEGUNDOS=2 RESTAURACION_REVISAR_SEGUNDOS=0 RESPALDO_FOTOS=0 \
    DB_HOST=127.0.0.1 DB_PORT=1 timeout 12 bash "$AQUI/respaldo.sh" 2>&1 || true)"
echo "$salida" | head -8
reintentos="$(echo "$salida" | grep -c 'se vuelve a intentar en 2s')"
siguio="$(echo "$salida" | grep -c 'Iniciando respaldo')"
if (( reintentos >= 2 && siguio >= 1 )); then echo "OK: reintentó $reintentos veces y siguió al soltarse el candado."; exit 0; fi
echo "FALLA: reintentos=$reintentos, siguió=$siguio"; exit 1
