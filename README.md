# MAC M1 Partner Backend

Spring Boot implementation for M1 Partner/Provider and identity management.

## Included

- Partner CRUD, approval, suspend/unsuspend and status history.
- Partner quotas and tenant-scoped user listing/invitation.
- Fixed RBAC roles and permissions.
- Admin account invitation.
- Login with JWT and optional TOTP 2FA.
- Profile and password management.
- Scoped personal API tokens for CI/CD.
- App-developer assignments through an M2 ownership port.
- Immutable-style audit records with actor/IP/user-agent/before/after.
- PostgreSQL Flyway migrations and Docker Compose.

## Start locally

Requirements: Java 21 and Maven 3.9+.

```bash
docker compose up -d postgres
export BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export BOOTSTRAP_ADMIN_PASSWORD='ChangeMe-123456'
mvn spring-boot:run
```

Health check: `GET http://localhost:8080/actuator/health`.

Swagger UI: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON: `/v3/api-docs`). Tài liệu chi tiết hơn (kèm bảng mã lỗi) ở [docs/api.md](docs/api.md).

## Important integration point

M1 intentionally does not own the `applications` table. M2 must replace:

- `NoopApplicationCountAdapter`
- `DenyApplicationOwnershipAdapter`

with adapters that query the M2 application module. Until then, Partner Admin app assignment fails closed; Platform ownership must not be guessed.

## Production notes

- Replace `JWT_SECRET` with a random secret of at least 32 bytes.
- Store secrets in a secret manager.
- Invitation tokens are returned by API only to make the module independently testable; production should deliver them through a notification worker.
- Put the service behind a trusted reverse proxy before relying on `X-Forwarded-For`.

## Deploy to AWS EC2

The workflow in `.github/workflows/deploy.yml` builds the Docker image on GitHub Actions, pushes it to the GitHub Container Registry (`ghcr.io`), and deploys the exact commit image to an EC2 instance when `main` is updated (or on manual dispatch). No AWS credentials are needed for the registry — pushing and pulling both authenticate with the workflow's built-in `GITHUB_TOKEN`.

The `deploy` job runs Postgres and the app as two containers on the EC2 instance itself via Docker Compose (no RDS): the app connects to Postgres over the Compose network at `jdbc:postgresql://postgres:5432/mac`, and only the app's port 8080 is published on the host.

Before enabling it:

1. Create a GitHub Environment named `production` (Settings → Environments) and add the secrets below to it.
2. Add `EC2_HOST`, `EC2_USER`, and `EC2_SSH_KEY` secrets. `EC2_PORT` is optional.
3. Install Docker (with the Compose plugin) on the EC2 instance. No AWS CLI or IAM role is needed — it authenticates to GHCR with `docker login` using the same `GITHUB_TOKEN` forwarded from the workflow run.
4. Add a `FILE_ENV` secret containing the runtime `.env` file content (one `KEY=value` per line): `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, and optionally `BOOTSTRAP_ADMIN_NAME`. `DB_URL` does not need to be set — the workflow points it at the Compose Postgres service.
5. Allow inbound TCP 8080 only as needed, preferably exposing the service through an HTTPS reverse proxy. Postgres is not published outside the instance.
6. The first successful run publishes a package under the repository on GitHub (Packages tab). If it's created private, no further action is needed since the deploy step authenticates before pulling.

Postgres data persists in a named Docker volume on the EC2 instance's own disk — there is no managed backup/HA. Snapshot the instance's EBS volume (or add a backup job) if you need durability guarantees beyond that.
