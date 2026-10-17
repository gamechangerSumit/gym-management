# Deployment

## Requirements
Docker 24+ with Compose v2, a Linux host (2 GB RAM is enough for one gym), a domain name for production.

## Local / LAN pilot (HTTP)
```
cp .env.example .env
# set POSTGRES_PASSWORD, APP_QR_ENC_KEY (openssl rand -base64 32), APP_BOOTSTRAP_OWNER_PASSWORD
docker compose up --build -d
open http://localhost:8080
```
Sign in as the bootstrap owner; you are forced to choose a new password. Camera scanning needs HTTPS
(browsers allow it on `localhost` only), so use the TLS setup below for real devices.

## Production (HTTPS)
1. Point DNS at the host. Obtain certificates (e.g. `certbot certonly --standalone -d your.domain`).
2. Copy `fullchain.pem` and `privkey.pem` into `deploy/certs/`.
3. In `.env`: `APP_PUBLIC_BASE_URL=https://your.domain`, `APP_COOKIE_SECURE=true`, `SPRING_PROFILES_ACTIVE=prod`.
4. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build`
5. Set up renewal (certbot timer + `docker compose restart web`) and the backup cron from `BACKUP_RESTORE.md`.

To rehearse TLS locally: `scripts/gen-dev-cert.sh` (self-signed; browser warning expected).

## Configuration reference
| Variable | Purpose |
|---|---|
| `POSTGRES_*` | database name, user, password |
| `APP_QR_ENC_KEY` | base64 of 32 bytes; encrypts the stored copy of QR tokens. Required in prod. Keep a copy somewhere safe, separate from DB backups. |
| `APP_BOOTSTRAP_OWNER_LOGIN/PASSWORD` | creates the first owner only when no owner exists |
| `APP_PUBLIC_BASE_URL` | base for invite and reset links |
| `APP_COOKIE_SECURE` | `true` whenever served over HTTPS |
| `SPRING_PROFILES_ACTIVE` | `prod`, or `demo` for sample data (never together) |

## Upgrades
`git pull && docker compose up -d --build`. Flyway applies new migrations on start. Take a backup first.

## Demo mode
`SPRING_PROFILES_ACTIVE=demo` seeds sample members and logins (`staff/DemoStaff1`, `demo.member1/DemoMember1`),
and the UI shows a DEMO banner. The seeder refuses to run with the `prod` profile.
