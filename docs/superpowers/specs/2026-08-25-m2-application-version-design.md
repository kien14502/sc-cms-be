# M2 — Application & Version Design

**Milestone:** M2 (Application/Version domain), per `docs/MAC_Design.md` §5.3 (Domain B)
**Scope:** Application list, Create application wizard, Application detail, Version list, Create version,
Artifact upload, Manifest validation, Version detail, Review history.

Out of scope for this milestone (explicitly deferred to later milestones): Permission Catalog (M3),
Capability (M4), Purpose/Intent/Registry (M5), Publish/Rollout/Rollback/Kill-switch (M6 release side),
Analytics (M7), Reviewer assignment queue.

---

## 1. Why / context

M1 already ships a minimal `ApplicationEntity` in `com.vnpt.mac.partner` package, used only so that
`ApplicationOwnershipPort`/`ApplicationCountPort` (consumed by `PartnerService` and
`ResourceAuthorizationService`) have something to query. Both adapters are commented as
"fail-closed until M2 provides the real adapter." The `com.vnpt.mac.applications` package already
exists with empty `controller/dto/entity/repository/service` directories — this is where the real
domain belongs.

## 2. Module placement

Move out of `partner.entity` / `partner.repository` into `applications.entity` / `applications.repository`:
- `ApplicationEntity`, `ApplicationStatus`, `ApplicationType`, `ApplicationRepository`

`ApplicationOwnershipAdapter` and `ApplicationCountAdapter` stay in `partner.service` (they implement
partner-side ports) but their import changes to `applications.repository.ApplicationRepository`. No other
partner code should need to change — `ApplicationOwnershipPort`/`ApplicationCountPort` interfaces are
unchanged.

New code lives entirely under `com.vnpt.mac.applications`:
```
applications/
  controller/  ApplicationController, VersionController
  dto/         ApplicationDtos, VersionDtos, ArtifactDtos, ReviewDtos
  entity/      ApplicationEntity, ApplicationStatus, ApplicationType,
               AppVersionEntity, VersionStatus, AppCategoryEntity,
               VersionArtifactEntity, ArtifactKind,
               VersionWebappConfigEntity, VersionModuleConfigEntity,
               ValidationRunEntity, ValidationStatus,
               ValidationFindingEntity, FindingSeverity,
               ReviewSubmissionEntity, SubmissionStatus,
               ReviewDecisionEntity, ReviewDecisionType
  repository/  ApplicationRepository, AppVersionRepository, AppCategoryRepository,
               VersionArtifactRepository, VersionWebappConfigRepository, VersionModuleConfigRepository,
               ValidationRunRepository, ValidationFindingRepository,
               ReviewSubmissionRepository, ReviewDecisionRepository
  service/     ApplicationService, VersionService, ArtifactStorageService,
               ManifestValidationService, ReviewService
```

## 3. Database migrations

`applications` and `app_versions` (V003/V004) already have every column needed — no `ALTER`.

### `V006__create_app_module_domain.sql`

```sql
CREATE TABLE version_artifacts (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    kind VARCHAR(20) NOT NULL,               -- ZIP / APK / AAB
    storage_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    signature_fingerprint VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_version_artifacts_version ON version_artifacts(version_id);

CREATE TABLE version_webapp_config (
    version_id UUID PRIMARY KEY REFERENCES app_versions(id),
    destination_url VARCHAR(500) NOT NULL,
    ssl_valid BOOLEAN NOT NULL DEFAULT FALSE,
    last_health_status INTEGER,
    last_checked_at TIMESTAMPTZ
);

CREATE TABLE version_module_config (
    version_id UUID PRIMARY KEY REFERENCES app_versions(id),
    module_namespace VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE validation_runs (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    status VARCHAR(20) NOT NULL,             -- RUNNING / PASSED / FAILED
    triggered_by UUID,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_validation_runs_version ON validation_runs(version_id);

CREATE TABLE validation_findings (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES validation_runs(id),
    rule_code VARCHAR(100) NOT NULL,
    severity VARCHAR(10) NOT NULL,           -- ERROR / WARNING / INFO
    message TEXT NOT NULL,
    context JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_validation_findings_run ON validation_findings(run_id);

CREATE TABLE review_submissions (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    review_round INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,             -- PENDING / APPROVED / REJECTED / CHANGES_REQUESTED
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_review_submissions_version ON review_submissions(version_id);

CREATE TABLE review_decisions (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES review_submissions(id),
    decision VARCHAR(20) NOT NULL,           -- APPROVE / REJECT / REQUEST_CHANGES
    feedback TEXT,
    decided_by UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_review_decisions_submission ON review_decisions(submission_id);

INSERT INTO app_categories(id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000301', 'UTILITIES', 'Utilities'),
    ('00000000-0000-0000-0000-000000000302', 'ENTERTAINMENT', 'Entertainment'),
    ('00000000-0000-0000-0000-000000000303', 'EDUCATION', 'Education'),
    ('00000000-0000-0000-0000-000000000304', 'PRODUCTIVITY', 'Productivity'),
    ('00000000-0000-0000-0000-000000000305', 'LIFESTYLE', 'Lifestyle');
```

### `V007__seed_m2_permissions.sql`

```sql
INSERT INTO permissions(id, code, description) VALUES
    ('00000000-0000-0000-0000-000000000114', 'app.read.all', 'Read every application'),
    ('00000000-0000-0000-0000-000000000115', 'app.read', 'Read scoped application'),
    ('00000000-0000-0000-0000-000000000116', 'app.create', 'Create application'),
    ('00000000-0000-0000-0000-000000000117', 'version.read', 'Read version'),
    ('00000000-0000-0000-0000-000000000118', 'version.create', 'Create version'),
    ('00000000-0000-0000-0000-000000000119', 'version.update', 'Update version metadata'),
    ('00000000-0000-0000-0000-000000000120', 'artifact.upload', 'Upload version artifact/config'),
    ('00000000-0000-0000-0000-000000000121', 'version.submit', 'Submit version for review'),
    ('00000000-0000-0000-0000-000000000122', 'version.review', 'Approve/Reject/Request changes on a version');

-- PLATFORM_ADMIN already gets every permission (role_permissions SELECT * in V002)

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions   -- PARTNER_ADMIN
WHERE code IN ('app.read', 'app.create', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000204', id FROM permissions   -- PARTNER_DEVELOPER
WHERE code IN ('app.read', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000205', id FROM permissions   -- REVIEWER
WHERE code IN ('app.read', 'version.read', 'version.review');
```

## 4. Entities (key behavior)

- `ApplicationEntity` — adds `deletedAt`, `killSwitchActive` field mapping (already columns in DB,
  currently unmapped) for completeness, even though no endpoint mutates `killSwitchActive`/`deletedAt`
  in this milestone. `create(partnerId, appType)` generates `appCode = "APP-" + random 8 hex`, status
  `DRAFT`. Method `activate()` (`DRAFT → ACTIVE`) called by `ReviewService` when a version's first
  `APPROVE` decision lands.
- `AppVersionEntity` — `create(appId, partnerId, versionCode, ...)` status `DRAFT`. Methods: `submit()`
  (`DRAFT|CHANGES_REQUESTED → IN_REVIEW`, increments `reviewRound`), `approve()` (`→ APPROVED`),
  `reject()` (`→ REJECTED`), `requestChanges()` (`→ CHANGES_REQUESTED`). Each throws
  `BusinessException(VERSION_STATUS_INVALID)` on an illegal source state. A guard method
  `assertEditable()` throws unless status is `DRAFT` or `CHANGES_REQUESTED`, called by every
  update/artifact-upload path.
- `VersionArtifactEntity` / `VersionWebappConfigEntity` / `VersionModuleConfigEntity` — one row per
  version (re-upload replaces: delete old row + old file, insert new). Creating/replacing only allowed
  when `AppVersionEntity.assertEditable()` passes.
- `ValidationRunEntity` / `ValidationFindingEntity` — `ValidationRunEntity.passed()` returns true iff no
  finding has `severity = ERROR`.
- `ReviewSubmissionEntity` / `ReviewDecisionEntity` — one submission per submit-call; one decision per
  submission (multiple submissions accumulate across rounds — this **is** the review history).

## 5. Service logic

### `ArtifactStorageService`
Local filesystem, directory from new config `mac.storage.artifacts-dir` (default `./data/artifacts`,
mirrors the project's local-first dev setup — no S3/MinIO dependency added). Stores at
`{artifacts-dir}/{versionId}/{uuid}-{originalFilename}`, returns `storageKey` (relative path) +
sha256 checksum + size. New config also adds `mac.storage.max-artifact-bytes` (default 100MB).

### `ManifestValidationService`
Runs synchronously inside the artifact-upload / config-upload call (no async job — no message queue in
this codebase). Creates one `ValidationRunEntity`, appends `ValidationFindingEntity` rows, and sets the
run's final status.

- **MiniApp (real)**: open the ZIP (`java.util.zip.ZipInputStream`), require `manifest.json` and
  `index.html` at the archive root (`ERROR` finding `MANIFEST_MISSING` / `INDEX_HTML_MISSING` if absent),
  parse `manifest.json` as JSON and require it to be a valid JSON object (`ERROR` `MANIFEST_INVALID_JSON`
  otherwise), check `sizeBytes <= max-artifact-bytes` (`ERROR` `ARTIFACT_TOO_LARGE`). Any pass emits an
  `INFO` finding.
- **Feature App (stub)**: check filename extension is `.apk`/`.aab` and size limit only. `signature_fingerprint`
  left null — real signature verification deferred (documented, not silently dropped).
- **WebApp (stub)**: `destinationUrl` must start with `https://` and pass basic URL syntax validation
  (`ERROR` `INVALID_DESTINATION_URL` otherwise); `sslValid` set true/false from that same check;
  `lastHealthStatus`/`lastCheckedAt` are **not** populated in this milestone (no outbound HTTP call) —
  left null, documented as deferred to a later milestone.
- **App Module (stub)**: `moduleNamespace` must be non-blank (`ERROR` `MODULE_NAMESPACE_REQUIRED`
  otherwise).
- **App2App**: no artifact/config step exists; `submit()` requires no `version_artifacts` /
  `*_config` row to exist for this app type (trivially satisfied, no validation call made).

### `VersionService.submit(versionId)`
Guards: version status is `DRAFT` or `CHANGES_REQUESTED`; for app types requiring an artifact/config
(all but `APP2APP`), the latest `ValidationRunEntity` for this version exists and `passed()`. Then:
`version.submit()` (state → `IN_REVIEW`, `reviewRound++`), creates a `ReviewSubmissionEntity`
(`review_round = version.reviewRound`, `status = PENDING`), audit-logs `VERSION_SUBMITTED`.

### `ReviewService.decide(versionId, decision, feedback)`
Guards: version status `IN_REVIEW`; caller has `version.review`. `feedback` required (non-blank) when
`decision = REJECT` or `REQUEST_CHANGES` (mirrors the existing `ReasonRequest` pattern used for
partner reject/suspend). Loads the latest `ReviewSubmissionEntity` for the version, creates a
`ReviewDecisionEntity`, updates the submission's `status`, and drives the version:
`APPROVE → version.approve()` (+ `application.activate()` if this is the application's first ever
`APPROVED` version — checked via `AppVersionRepository.existsByAppIdAndStatus(appId, APPROVED)` before
applying), `REJECT → version.reject()`, `REQUEST_CHANGES → version.requestChanges()`. Audit-logs
`VERSION_REVIEW_DECIDED`.

## 6. API surface

Base path `/api/v1/applications`. Response envelope/pagination follow the existing `ApiResponse<T>` /
`PageResponse<T>` conventions; errors follow `ErrorCode` + `GlobalExceptionHandler`.

| Method & path | Purpose | Permission |
|---|---|---|
| `GET /applications` | List (filters: `status`, `appType`, `page`, `size`; partner-scoped unless `app.read.all`) | `app.read.all` or `app.read` |
| `POST /applications` | Create App shell + version 1 (`DRAFT`) in one transaction | `app.create` |
| `GET /applications/{id}` | Application detail (+ latest version summary, version count) | `app.read` + `@resourceAuth.app(#id)` |
| `GET /applications/{id}/versions` | Version list (filter `status`) | `version.read` + `@resourceAuth.app(#id)` |
| `POST /applications/{id}/versions` | Create version (`version_code` = max+1, `DRAFT`) | `version.create` + `@resourceAuth.app(#id)` |
| `GET /applications/{appId}/versions/{id}` | Version detail (metadata + artifact/config + latest validation + submission history summary) | `version.read` + `@resourceAuth.app(#appId)` |
| `PATCH /applications/{appId}/versions/{id}` | Update version metadata (only while editable) | `version.update` + `@resourceAuth.app(#appId)` |
| `POST /applications/{appId}/versions/{id}/artifact` (multipart) | Upload ZIP/APK/AAB — MiniApp/FeatureApp | `artifact.upload` + `@resourceAuth.app(#appId)` |
| `PUT /applications/{appId}/versions/{id}/webapp-config` | Set destination URL — WebApp | `artifact.upload` + `@resourceAuth.app(#appId)` |
| `PUT /applications/{appId}/versions/{id}/module-config` | Set module metadata — App Module | `artifact.upload` + `@resourceAuth.app(#appId)` |
| `GET /applications/{appId}/versions/{id}/validation` | Latest validation run + findings | `version.read` + `@resourceAuth.app(#appId)` |
| `POST /applications/{appId}/versions/{id}/submit` | Submit for review | `version.submit` + `@resourceAuth.app(#appId)` |
| `POST /applications/{appId}/versions/{id}/review-decisions` | Approve/Reject/Request changes | `version.review` |
| `GET /applications/{appId}/versions/{id}/review-history` | All submissions + their decisions, oldest first | `version.read` + `@resourceAuth.app(#appId)` |

Request/response DTO shapes follow the existing style (records nested inside `XxxDtos`, e.g.
`ApplicationDtos.CreateApplicationRequest`, `VersionDtos.VersionResponse`, mirroring
`PartnerDtos`/`UserDtos`).

### New `ErrorCode` entries
`APPLICATION_NOT_FOUND` (404), `VERSION_NOT_FOUND` (404), `VERSION_STATUS_INVALID` (409),
`VERSION_NOT_EDITABLE` (409), `ARTIFACT_TYPE_MISMATCH` (400, e.g. uploading a ZIP for a WebApp),
`ARTIFACT_MISSING` (400, e.g. submit without an uploaded artifact), `VALIDATION_FAILED` (409, submit
blocked by an `ERROR` finding), `REVIEW_FEEDBACK_REQUIRED` (400).

## 7. State machine (final, in-scope)

```
DRAFT/CHANGES_REQUESTED --submit (validation must PASS)--> IN_REVIEW
IN_REVIEW --approve--> APPROVED               (terminal this milestone; Publish is M6, not built)
IN_REVIEW --reject--> REJECTED                (terminal, matches doc's default recommendation)
IN_REVIEW --request changes--> CHANGES_REQUESTED --submit--> IN_REVIEW (review_round + 1)
```

Deliberately dropped vs. the full doc (§3.3), both because the dependency doesn't exist yet:
- No persisted `PENDING_VALIDATION` state — validation is synchronous at upload time, so `submit` just
  checks the latest `validation_run`.
- No `PUBLISHING`/`PUBLISHED`/`UNPUBLISHED`/`DEPRECATED` — `Application.status` flips `DRAFT → ACTIVE`
  on first `APPROVED` version as a stand-in until the Release milestone exists.

Kill-switch and Archive endpoints are not built (not in the requested 9 items); the DB columns already
exist for when that milestone lands.

## 8. Testing

Mirror `PartnerEntityTest` / `TotpServiceTest` conventions (JUnit 5, H2 for repository-touching tests):
- Entity tests: `AppVersionEntity` state-transition guards (valid/invalid transitions), `assertEditable()`.
- `ManifestValidationServiceTest`: real ZIP fixtures (valid MiniApp, missing `manifest.json`, missing
  `index.html`, oversize) asserting correct findings/severities.
- `VersionService`/`ReviewService` tests: submit blocked without passed validation, submit blocked from
  wrong state, review decision requires feedback on REJECT/REQUEST_CHANGES, APPROVE flips application to
  ACTIVE only on the first approval.
- Controller-level tests for the permission/resource-scoping matrix (partner-scoped list, `resourceAuth.app`
  enforcement for `PARTNER_DEVELOPER` on unassigned apps), following `AppAssignmentController`'s pattern.

## 9. Documentation

Extend `docs/api.md` with the new endpoint group once implemented (same format as the existing Partner
section), and add a Domain B ERD/module note pointer back to `docs/MAC_Design.md` §5.3.
