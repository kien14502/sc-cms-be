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
