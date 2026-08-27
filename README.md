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

The workflow in `.github/workflows/deploy.yml` builds the Docker image, pushes it to Amazon ECR, and deploys the exact commit image to an EC2 instance when `main` is updated (or on manual dispatch).

The `deploy` job runs Postgres and the app as two containers on the EC2 instance itself via Docker Compose (no RDS): the app connects to Postgres over the Compose network at `jdbc:postgresql://postgres:5432/mac`, and only the app's port 8080 is published on the host.

Before enabling it:

1. Create an ECR repository and configure repository variables `AWS_REGION` and `ECR_REPOSITORY`.
2. Create a GitHub Environment named `production` (Settings → Environments) and add the secrets below to it.
3. Add secrets `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` with permission to push to ECR.
4. Add `EC2_HOST`, `EC2_USER`, and `EC2_SSH_KEY` secrets. `EC2_PORT` is optional.
5. Give the EC2 instance an IAM role with ECR pull permissions, and install Docker (with the Compose plugin), AWS CLI, and `curl`.
6. Add a `FILE_ENV` secret containing the runtime `.env` file content (one `KEY=value` per line): `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, and optionally `BOOTSTRAP_ADMIN_NAME`. `DB_URL` does not need to be set — the workflow points it at the Compose Postgres service. The workflow writes this secret to `.env` next to the generated `docker-compose.yml` on the instance.
7. Allow inbound TCP 8080 only as needed, preferably exposing the service through an HTTPS reverse proxy. Postgres is not published outside the instance.

Postgres data persists in a named Docker volume on the EC2 instance's own disk — there is no managed backup/HA. Snapshot the instance's EBS volume (or add a backup job) if you need durability guarantees beyond that.
