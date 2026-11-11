#!/usr/bin/env sh
# Genera un .env con secretos aleatorios para docker-compose.yml (AuthCore).
# No sobrescribe un .env existente. Uso: deploy/generar-env.sh
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
    echo "Ya existe .env; no se sobrescribe." >&2
    echo "Bórralo o edítalo a mano si quieres regenerarlo." >&2
    exit 1
fi

aleatorio() {
    openssl rand -hex "$1"
}

cat > .env <<EOF
# Generado por deploy/generar-env.sh el $(date -u +%Y-%m-%dT%H:%M:%SZ)
DB_ROOT_PASSWORD=$(aleatorio 16)
DB_PASSWORD=$(aleatorio 16)
JWT_SECRET=$(aleatorio 32)
ADMIN_PASSWORD=$(aleatorio 12)
EOF

chmod 600 .env
echo "Creado .env con permisos 600."
echo "Recuerda que el dominio necesita JWT_SECRET_BASE64 = el mismo JWT_SECRET en Base64."
