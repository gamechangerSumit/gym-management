#!/bin/sh
# Self-signed certificate for trying the TLS setup locally. Browsers will warn; use real certs in production.
set -e
mkdir -p "$(dirname "$0")/../deploy/certs"
openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" \
  -keyout "$(dirname "$0")/../deploy/certs/privkey.pem" -out "$(dirname "$0")/../deploy/certs/fullchain.pem"
echo "Wrote deploy/certs/{fullchain,privkey}.pem (dev only)"
