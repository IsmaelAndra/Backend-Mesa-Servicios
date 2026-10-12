#!/bin/sh
# ==============================================================================
# Copia de seguridad de la Mesa de Servicios: base de datos + archivos subidos.
#
#   ./respaldo.sh                 deja las copias en ~/respaldos
#   DESTINO=/mnt/nas ./respaldo.sh
#   DIAS=30 ./respaldo.sh         borra las copias de más de 30 días (14 por defecto)
#
# Para cron (cada noche a las 02:30):
#   30 2 * * * cd /opt/mesa-servicios/Backend-Mesa-Servicios/despliegue && ./respaldo.sh >> ~/respaldos/respaldo.log 2>&1
#
# Restaurar: INSTALACION.md, punto 4.2. Copia el destino FUERA de este servidor.
# ==============================================================================
set -eu

cd "$(dirname "$0")"
DESTINO="${DESTINO:-$HOME/respaldos}"
DIAS="${DIAS:-14}"
FECHA="$(date +%Y%m%d-%H%M)"
PROYECTO="$(docker compose config --format json 2>/dev/null | sed -n 's/^ *"name": *"\([^"]*\)".*/\1/p' | head -n 1)"
PROYECTO="${PROYECTO:-mesa-servicios}"

mkdir -p "$DESTINO"
umask 077   # las copias tienen datos personales: solo para el dueño

echo "[$(date '+%F %T')] Base de datos..."
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
    > "$DESTINO/bd-$FECHA.dump.tmp"
mv "$DESTINO/bd-$FECHA.dump.tmp" "$DESTINO/bd-$FECHA.dump"

echo "[$(date '+%F %T')] Archivos subidos..."
docker run --rm -v "${PROYECTO}_uploads:/datos:ro" -v "$DESTINO:/respaldo" alpine \
    tar czf "/respaldo/uploads-$FECHA.tar.gz" -C /datos .

echo "[$(date '+%F %T')] Borrando copias de más de $DIAS días..."
find "$DESTINO" -maxdepth 1 \( -name 'bd-*.dump' -o -name 'uploads-*.tar.gz' \) -mtime +"$DIAS" -delete

echo "[$(date '+%F %T')] Listo: $DESTINO/bd-$FECHA.dump y uploads-$FECHA.tar.gz"
