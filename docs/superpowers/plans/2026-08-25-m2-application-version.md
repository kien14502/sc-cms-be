# M2 Application & Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the real Application/Version domain (M2) on top of MAC's Spring Boot backend: application list/create/detail, version list/create/detail/update, artifact upload with manifest validation, and a submit → review-decision workflow with review history.

**Architecture:** New `com.vnpt.mac.applications` module (`controller/dto/entity/repository/service`) mirroring the existing `partner` module's layout and conventions exactly (record DTOs nested in `XxxDtos`, static entity factories with behavior methods, `ApiResponse`/`PageResponse` envelopes, `@PreAuthorize` + `@resourceAuth.app`). The pre-existing placeholder `ApplicationEntity` in `partner.entity` moves into this new module; `partner`'s ownership/count adapters are repointed to it. Artifact validation runs synchronously at upload time (no job queue exists in this codebase) instead of the full async `PENDING_VALIDATION` state from the design doc.

**Tech Stack:** Spring Boot 4.1.1, Java 21, Spring Data JPA + Hibernate (Jackson-based JSON mapping via `@JdbcTypeCode(SqlTypes.JSON)`), PostgreSQL 17 + Flyway, Spring Security method security, JUnit 5 + AssertJ, local filesystem artifact storage (no object storage dependency).

**Spec:** `docs/superpowers/specs/2026-08-25-m2-application-version-design.md`

## Global Constraints

- Follow the existing package layout exactly: `controller/dto/entity/repository/service` under `com.vnpt.mac.applications`.
- DTOs are `record`s nested inside a single `final class XxxDtos` per feature area (never top-level DTO files), matching `PartnerDtos`/`UserDtos`.
- Entities: package-private no-arg constructor, `static create(...)` factory, behavior methods (never public setters), plain getters at the bottom.
- Services: constructor injection (no `@Autowired` field injection), `@Transactional` per public method, throw `new BusinessException(ErrorCode.X, "message")`, call `audit.log(partnerId, ACTION, resourceType, resourceId, before, after)` on every mutation.
- Controllers: `@PreAuthorize` with the exact permission codes from the migration, wrap every response in `ApiResponse.success(...)`, paginate with `PageResponse.from(...)` and `Math.min(size, 100)`.
- No new ErrorCode without adding it to `ErrorCode.java` first.
- No object storage / message queue dependency is added — local filesystem + synchronous validation only, per the approved design.
- Vietnamese-language `@Operation` summaries/descriptions and validation/error messages, matching the existing partner module.

---

### Task 1: Flyway migrations for the M2 schema + permissions

**Files:**
- Create: `src/main/resources/db/migration/V006__create_app_module_domain.sql`
- Create: `src/main/resources/db/migration/V007__seed_m2_permissions.sql`

**Interfaces:**
- Produces: tables `version_artifacts`, `version_webapp_config`, `version_module_config`, `validation_runs`, `validation_findings`, `review_submissions`, `review_decisions`; seeded `app_categories` rows; permission codes `app.read.all`, `app.read`, `app.create`, `version.read`, `version.create`, `version.update`, `artifact.upload`, `version.submit`, `version.review` granted to the existing roles `PARTNER_ADMIN` (`00000000-0000-0000-0000-000000000203`), `PARTNER_DEVELOPER` (`...204`), `REVIEWER` (`...205`). `PLATFORM_ADMIN` already receives every permission via the `SELECT * FROM permissions` insert in `V002`.

- [ ] **Step 1: Write `V006__create_app_module_domain.sql`**

```sql
CREATE TABLE version_artifacts (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    kind VARCHAR(20) NOT NULL,
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
    status VARCHAR(20) NOT NULL,
    triggered_by UUID,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_validation_runs_version ON validation_runs(version_id);

CREATE TABLE validation_findings (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES validation_runs(id),
    rule_code VARCHAR(100) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    context JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_validation_findings_run ON validation_findings(run_id);

CREATE TABLE review_submissions (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES app_versions(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    review_round INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_review_submissions_version ON review_submissions(version_id);

CREATE TABLE review_decisions (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES review_submissions(id),
    decision VARCHAR(20) NOT NULL,
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

- [ ] **Step 2: Write `V007__seed_m2_permissions.sql`**

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

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000203', id FROM permissions
WHERE code IN ('app.read', 'app.create', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000204', id FROM permissions
WHERE code IN ('app.read', 'version.read', 'version.create', 'version.update',
               'artifact.upload', 'version.submit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000205', id FROM permissions
WHERE code IN ('app.read', 'version.read', 'version.review');
```

- [ ] **Step 3: Verify migrations apply cleanly against real Postgres**

Run:
```bash
docker compose up -d postgres
mvn -q -DskipTests spring-boot:run > /tmp/mac-m2-migration-check.log 2>&1 &
APP_PID=$!
for i in $(seq 1 30); do curl -sf http://localhost:8080/actuator/health && break; sleep 2; done
curl -s http://localhost:8080/actuator/health
grep -i "Successfully applied" /tmp/mac-m2-migration-check.log
kill $APP_PID
```
Expected: health returns `{"status":"UP"}` and the log shows `Successfully applied 2 migrations` (V006, V007) with no Flyway errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V006__create_app_module_domain.sql src/main/resources/db/migration/V007__seed_m2_permissions.sql
git commit -m "feat(db): add M2 application/version domain tables and permissions"
```

---

### Task 2: Move the placeholder Application domain into the `applications` module

**Files:**
- Create: `src/main/java/com/vnpt/mac/applications/entity/ApplicationEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ApplicationStatus.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ApplicationType.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/ApplicationRepository.java`
- Delete: `src/main/java/com/vnpt/mac/partner/entity/ApplicationEntity.java`
- Delete: `src/main/java/com/vnpt/mac/partner/entity/ApplicationStatus.java`
- Delete: `src/main/java/com/vnpt/mac/partner/entity/ApplicationType.java`
- Delete: `src/main/java/com/vnpt/mac/partner/repository/ApplicationRepository.java`
- Modify: `src/main/java/com/vnpt/mac/partner/service/ApplicationOwnershipAdapter.java`
- Modify: `src/main/java/com/vnpt/mac/partner/service/ApplicationCountAdapter.java`

**Interfaces:**
- Produces: `ApplicationEntity.create(UUID partnerId, ApplicationType appType)` → new entity, status `DRAFT`, `appCode` auto-generated as `"APP-" + 8 random hex chars`. Getters: `getId()`, `getPartnerId()`, `getAppCode()`, `getAppType()`, `getStatus()`, `isFirstParty()`, `isKillSwitchActive()`, `getDeletedAt()`. Behavior: `activate()` (`DRAFT → ACTIVE`, no-op otherwise), `archive()` (`→ ARCHIVED`).
- Produces: `ApplicationRepository` with `existsByIdAndPartnerId(UUID, UUID)` and `countByPartnerIdAndStatus(UUID, ApplicationStatus)` (unchanged signatures — Task 4 adds a `search` method later).
- Consumed by: `partner.service.ApplicationOwnershipAdapter`, `partner.service.ApplicationCountAdapter` (import path only changes; behavior is identical).

- [ ] **Step 1: Create the new entity/enum/repository files under `applications`**

`src/main/java/com/vnpt/mac/applications/entity/ApplicationStatus.java`:
```java
package com.vnpt.mac.applications.entity;

public enum ApplicationStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED,
    KILLED
}
```

`src/main/java/com/vnpt/mac/applications/entity/ApplicationType.java`:
```java
package com.vnpt.mac.applications.entity;

public enum ApplicationType {
    MINIAPP,
    WEBAPP,
    APP2APP,
    APP_MODULE,
    FEATURE_APP
}
```

`src/main/java/com/vnpt/mac/applications/entity/ApplicationEntity.java`:
```java
package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class ApplicationEntity extends BaseAuditEntity {
    @Id
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "app_code", nullable = false, unique = true, length = 50)
    private String appCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 30)
    private ApplicationType appType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "is_first_party", nullable = false)
    private boolean firstParty;

    @Column(name = "kill_switch_active", nullable = false)
    private boolean killSwitchActive;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ApplicationEntity() {}

    public static ApplicationEntity create(UUID partnerId, ApplicationType appType) {
        var entity = new ApplicationEntity();
        entity.id = UUID.randomUUID();
        entity.partnerId = partnerId;
        entity.appCode = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        entity.appType = appType;
        entity.status = ApplicationStatus.DRAFT;
        entity.firstParty = false;
        entity.killSwitchActive = false;
        return entity;
    }

    public void activate() {
        if (status == ApplicationStatus.DRAFT) status = ApplicationStatus.ACTIVE;
    }

    public void archive() {
        status = ApplicationStatus.ARCHIVED;
    }

    public UUID getId() { return id; }
    public UUID getPartnerId() { return partnerId; }
    public String getAppCode() { return appCode; }
    public ApplicationType getAppType() { return appType; }
    public ApplicationStatus getStatus() { return status; }
    public boolean isFirstParty() { return firstParty; }
    public boolean isKillSwitchActive() { return killSwitchActive; }
    public Instant getDeletedAt() { return deletedAt; }
}
```

`src/main/java/com/vnpt/mac/applications/repository/ApplicationRepository.java`:
```java
package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {
    boolean existsByIdAndPartnerId(UUID id, UUID partnerId);
    long countByPartnerIdAndStatus(UUID partnerId, ApplicationStatus status);
}
```

- [ ] **Step 2: Delete the old files in `partner`**

```bash
git rm src/main/java/com/vnpt/mac/partner/entity/ApplicationEntity.java
git rm src/main/java/com/vnpt/mac/partner/entity/ApplicationStatus.java
git rm src/main/java/com/vnpt/mac/partner/entity/ApplicationType.java
git rm src/main/java/com/vnpt/mac/partner/repository/ApplicationRepository.java
```

- [ ] **Step 3: Repoint the two partner adapters to the new package**

In `src/main/java/com/vnpt/mac/partner/service/ApplicationOwnershipAdapter.java`, change the import:
```java
import com.vnpt.mac.applications.repository.ApplicationRepository;
```
(replacing `import com.vnpt.mac.partner.repository.ApplicationRepository;`). No other change — the class body already only calls `applications.existsByIdAndPartnerId(appId, partnerId)`.

In `src/main/java/com/vnpt/mac/partner/service/ApplicationCountAdapter.java`, change both imports:
```java
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.repository.ApplicationRepository;
```
(replacing the `partner.entity.ApplicationStatus` / `partner.repository.ApplicationRepository` imports). No other change.

- [ ] **Step 4: Verify the whole module still compiles and the existing test suite passes**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all existing tests (`PartnerEntityTest`, `TotpServiceTest`) still pass — this proves `AppAssignmentController`/`AppAssignmentService`/`ResourceAuthorizationService`/`PartnerService` (all of which transitively depend on `ApplicationOwnershipPort`/`ApplicationCountPort`) compile fine against the moved types.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: move Application entity/repository from partner into the applications module"
```

---

### Task 3: `AppVersionEntity` state machine, `AppCategoryEntity`, and new error codes

**Files:**
- Modify: `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/VersionStatus.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/AppCategoryEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/AppVersionEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/AppCategoryRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/AppVersionRepository.java`
- Test: `src/test/java/com/vnpt/mac/applications/entity/AppVersionEntityTest.java`

**Interfaces:**
- Produces: `VersionStatus{DRAFT, IN_REVIEW, APPROVED, REJECTED, CHANGES_REQUESTED}`.
- Produces: `AppVersionEntity.create(UUID appId, UUID partnerId, int versionCode, String versionName, String displayName, String packageName, String descriptionShort, String descriptionLong, List<String> supportedLanguages)` → status `DRAFT`, `reviewRound = 0`.
- Produces on `AppVersionEntity`: `assertEditable()` (throws `VERSION_NOT_EDITABLE` unless `DRAFT`/`CHANGES_REQUESTED`), `submit()` (`DRAFT|CHANGES_REQUESTED → IN_REVIEW`, `reviewRound++`), `approve()`/`reject()`/`requestChanges()` (all require `IN_REVIEW`, throw `VERSION_STATUS_INVALID` otherwise), `updateMetadata(displayName, descriptionShort, descriptionLong, supportedLanguages)`, `replaceCategories(Set<AppCategoryEntity>)`. Getters: `getId/getAppId/getPartnerId/getVersionCode/getVersionName/getStatus/getDisplayName/getPackageName/getDescriptionShort/getDescriptionLong/getSupportedLanguages/getReviewRound/getCategories/getRevision` (the last from `BaseAuditEntity`).
- Produces: `AppCategoryEntity` — read-only lookup (`getId/getCode/getName`), no factory (rows come from the V006 seed).
- Produces: `AppVersionRepository.findByAppId(UUID, Pageable)`, `.findByAppIdAndStatus(UUID, VersionStatus, Pageable)`, `.findTopByAppIdOrderByVersionCodeDesc(UUID)`, `.countByAppId(UUID)`, `.existsByAppIdAndStatus(UUID, VersionStatus)`.
- Produces: `AppCategoryRepository.findByCodeIn(Collection<String>)`.
- Adds to `ErrorCode`: `APPLICATION_NOT_FOUND(404)`, `VERSION_NOT_FOUND(404)`, `VERSION_STATUS_INVALID(409)`, `VERSION_NOT_EDITABLE(409)`, `CATEGORY_NOT_FOUND(404)`.

- [ ] **Step 1: Add the new error codes**

In `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`, add before the closing `;`:
```java
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_STATUS_INVALID(HttpStatus.CONFLICT),
    VERSION_NOT_EDITABLE(HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
```

- [ ] **Step 2: Write `VersionStatus.java`**

```java
package com.vnpt.mac.applications.entity;

public enum VersionStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED
}
```

- [ ] **Step 3: Write `AppCategoryEntity.java`**

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_categories")
public class AppCategoryEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    protected AppCategoryEntity() {}

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
```

- [ ] **Step 4: Write `AppVersionEntity.java`**

```java
package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.common.persistence.BaseAuditEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_versions")
public class AppVersionEntity extends BaseAuditEntity {
    @Id
    private UUID id;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VersionStatus status;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "description_short", length = 500)
    private String descriptionShort;

    @Column(name = "description_long")
    private String descriptionLong;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_languages", nullable = false, columnDefinition = "jsonb")
    private List<String> supportedLanguages = List.of();

    @Column(name = "review_round", nullable = false)
    private int reviewRound;

    @ManyToMany
    @JoinTable(name = "app_version_categories",
            joinColumns = @JoinColumn(name = "version_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<AppCategoryEntity> categories = new HashSet<>();

    protected AppVersionEntity() {}

    public static AppVersionEntity create(UUID appId, UUID partnerId, int versionCode, String versionName,
                                          String displayName, String packageName, String descriptionShort,
                                          String descriptionLong, List<String> supportedLanguages) {
        var entity = new AppVersionEntity();
        entity.id = UUID.randomUUID();
        entity.appId = appId;
        entity.partnerId = partnerId;
        entity.versionCode = versionCode;
        entity.versionName = versionName.trim();
        entity.status = VersionStatus.DRAFT;
        entity.displayName = displayName.trim();
        entity.packageName = packageName.trim();
        entity.descriptionShort = descriptionShort;
        entity.descriptionLong = descriptionLong;
        entity.supportedLanguages = supportedLanguages == null ? List.of() : supportedLanguages;
        entity.reviewRound = 0;
        return entity;
    }

    public void updateMetadata(String displayName, String descriptionShort, String descriptionLong, List<String> supportedLanguages) {
        this.displayName = displayName.trim();
        this.descriptionShort = descriptionShort;
        this.descriptionLong = descriptionLong;
        this.supportedLanguages = supportedLanguages == null ? List.of() : supportedLanguages;
    }

    public void replaceCategories(Set<AppCategoryEntity> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }

    public void assertEditable() {
        if (status != VersionStatus.DRAFT && status != VersionStatus.CHANGES_REQUESTED)
            throw new BusinessException(ErrorCode.VERSION_NOT_EDITABLE, "Version ở trạng thái " + status + " không thể chỉnh sửa");
    }

    public void submit() {
        assertEditable();
        status = VersionStatus.IN_REVIEW;
        reviewRound += 1;
    }

    public void approve() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.APPROVED;
    }

    public void reject() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.REJECTED;
    }

    public void requestChanges() {
        requireStatus(VersionStatus.IN_REVIEW);
        status = VersionStatus.CHANGES_REQUESTED;
    }

    private void requireStatus(VersionStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.VERSION_STATUS_INVALID,
                "Không thể chuyển Version từ " + status);
    }

    public UUID getId() { return id; }
    public UUID getAppId() { return appId; }
    public UUID getPartnerId() { return partnerId; }
    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public VersionStatus getStatus() { return status; }
    public String getDisplayName() { return displayName; }
    public String getPackageName() { return packageName; }
    public String getDescriptionShort() { return descriptionShort; }
    public String getDescriptionLong() { return descriptionLong; }
    public List<String> getSupportedLanguages() { return supportedLanguages; }
    public int getReviewRound() { return reviewRound; }
    public Set<AppCategoryEntity> getCategories() { return Set.copyOf(categories); }
}
```

- [ ] **Step 5: Write `AppCategoryRepository.java` and `AppVersionRepository.java`**

```java
package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppCategoryEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppCategoryRepository extends JpaRepository<AppCategoryEntity, UUID> {
    List<AppCategoryEntity> findByCodeIn(Collection<String> codes);
}
```

```java
package com.vnpt.mac.applications.repository;

import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, UUID> {
    Page<AppVersionEntity> findByAppId(UUID appId, Pageable pageable);
    Page<AppVersionEntity> findByAppIdAndStatus(UUID appId, VersionStatus status, Pageable pageable);
    Optional<AppVersionEntity> findTopByAppIdOrderByVersionCodeDesc(UUID appId);
    long countByAppId(UUID appId);
    boolean existsByAppIdAndStatus(UUID appId, VersionStatus status);
}
```

- [ ] **Step 6: Write the failing test first — `AppVersionEntityTest.java`**

```java
package com.vnpt.mac.applications.entity;

import com.vnpt.mac.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class AppVersionEntityTest {
    private AppVersionEntity draft() {
        return AppVersionEntity.create(UUID.randomUUID(), UUID.randomUUID(), 1, "1.0.0",
                "My MiniApp", "com.vnpt.miniapp", "short", "long", List.of("vi", "en"));
    }

    @Test void draftVersionCanBeSubmitted() {
        var v = draft();
        v.submit();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(v.getReviewRound()).isEqualTo(1);
    }

    @Test void inReviewVersionCanBeApproved() {
        var v = draft();
        v.submit();
        v.approve();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.APPROVED);
    }

    @Test void inReviewVersionCanBeSentBackForChangesThenResubmitted() {
        var v = draft();
        v.submit();
        v.requestChanges();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.CHANGES_REQUESTED);
        v.submit();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(v.getReviewRound()).isEqualTo(2);
    }

    @Test void approvedVersionCannotBeEdited() {
        var v = draft();
        v.submit();
        v.approve();
        assertThatThrownBy(v::assertEditable).isInstanceOf(BusinessException.class);
    }

    @Test void draftVersionCannotBeApprovedDirectly() {
        assertThatThrownBy(draft()::approve).isInstanceOf(BusinessException.class);
    }

    @Test void rejectedVersionIsTerminal() {
        var v = draft();
        v.submit();
        v.reject();
        assertThat(v.getStatus()).isEqualTo(VersionStatus.REJECTED);
        assertThatThrownBy(v::submit).isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn test -Dtest=AppVersionEntityTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/vnpt/mac/common/exception/ErrorCode.java \
        src/main/java/com/vnpt/mac/applications/entity/VersionStatus.java \
        src/main/java/com/vnpt/mac/applications/entity/AppCategoryEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/AppVersionEntity.java \
        src/main/java/com/vnpt/mac/applications/repository/AppCategoryRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/AppVersionRepository.java \
        src/test/java/com/vnpt/mac/applications/entity/AppVersionEntityTest.java
git commit -m "feat(applications): add AppVersion state machine and category lookup"
```

---

### Task 4: Application list + create wizard (`ApplicationService`, `ApplicationController`)

**Files:**
- Modify: `src/main/java/com/vnpt/mac/applications/repository/ApplicationRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/dto/VersionDtos.java`
- Create: `src/main/java/com/vnpt/mac/applications/dto/ApplicationDtos.java`
- Create: `src/main/java/com/vnpt/mac/applications/service/VersionService.java`
- Create: `src/main/java/com/vnpt/mac/applications/service/ApplicationService.java`
- Create: `src/main/java/com/vnpt/mac/applications/controller/ApplicationController.java`

**Interfaces:**
- Consumes: `AppVersionEntity`, `AppVersionRepository`, `AppCategoryEntity`, `AppCategoryRepository`, `ApplicationEntity`, `ApplicationRepository` (Task 2/3). `AuditService.log(UUID, String, String, UUID, Object, Object)`, `CurrentUser.require()/id()/partnerId()` (existing).
- Produces: `VersionDtos.VersionMetadataFields(versionName, displayName, packageName, descriptionShort, descriptionLong, supportedLanguages, categoryCodes)`, `VersionDtos.UpdateVersionRequest(displayName, descriptionShort, descriptionLong, supportedLanguages, categoryCodes)`, `VersionDtos.VersionResponse.from(AppVersionEntity)`.
- Produces: `ApplicationDtos.CreateApplicationRequest(appType, version)`, `ApplicationDtos.ApplicationResponse.from(ApplicationEntity, long versionCount, VersionResponse latestVersion)`.
- Produces on `VersionService` (used again in Tasks 5, 8, 9): `AppVersionEntity requireVersion(UUID appId, UUID versionId)` (public), package-private `AppVersionEntity createInitialVersion(UUID appId, UUID partnerId, VersionMetadataFields fields)` (called only by `ApplicationService`, same package).
- Produces on `ApplicationService`: `ApplicationEntity require(UUID id)` (public, reused nowhere else yet but mirrors `PartnerService.require`).

- [ ] **Step 1: Add the `search` query to `ApplicationRepository`**

Add to `src/main/java/com/vnpt/mac/applications/repository/ApplicationRepository.java` (inside the interface, alongside the two existing methods):
```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.vnpt.mac.applications.entity.ApplicationType;
```
```java
    @Query("SELECT a FROM ApplicationEntity a WHERE (:partnerId IS NULL OR a.partnerId = :partnerId) " +
           "AND (:status IS NULL OR a.status = :status) AND (:appType IS NULL OR a.appType = :appType)")
    Page<ApplicationEntity> search(@Param("partnerId") UUID partnerId, @Param("status") ApplicationStatus status,
                                    @Param("appType") ApplicationType appType, Pageable pageable);
```

- [ ] **Step 2: Write `VersionDtos.java`**

```java
package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.AppVersionEntity;
import com.vnpt.mac.applications.entity.VersionStatus;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VersionDtos {
    private VersionDtos() {}

    public record VersionMetadataFields(@NotBlank @Size(max = 50) String versionName,
                                        @NotBlank @Size(max = 255) String displayName,
                                        @NotBlank @Size(max = 255) String packageName,
                                        @Size(max = 500) String descriptionShort,
                                        String descriptionLong,
                                        List<String> supportedLanguages,
                                        Set<String> categoryCodes) {
    }

    public record UpdateVersionRequest(@NotBlank @Size(max = 255) String displayName,
                                       @Size(max = 500) String descriptionShort,
                                       String descriptionLong,
                                       List<String> supportedLanguages,
                                       Set<String> categoryCodes) {
    }

    public record VersionResponse(UUID id, UUID appId, int versionCode, String versionName, VersionStatus status,
                                  String displayName, String packageName, String descriptionShort, String descriptionLong,
                                  List<String> supportedLanguages, Set<String> categoryCodes, int reviewRound, long revision) {
        public static VersionResponse from(AppVersionEntity v) {
            return new VersionResponse(v.getId(), v.getAppId(), v.getVersionCode(), v.getVersionName(), v.getStatus(),
                    v.getDisplayName(), v.getPackageName(), v.getDescriptionShort(), v.getDescriptionLong(),
                    v.getSupportedLanguages(), v.getCategories().stream().map(c -> c.getCode()).collect(Collectors.toSet()),
                    v.getReviewRound(), v.getRevision());
        }
    }
}
```

- [ ] **Step 3: Write `ApplicationDtos.java`**

```java
package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.dto.VersionDtos.VersionMetadataFields;
import com.vnpt.mac.applications.dto.VersionDtos.VersionResponse;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record CreateApplicationRequest(@NotNull ApplicationType appType,
                                           @NotNull @Valid VersionMetadataFields version) {
    }

    public record ApplicationResponse(UUID id, String appCode, ApplicationType appType, ApplicationStatus status,
                                      boolean firstParty, boolean killSwitchActive, UUID partnerId,
                                      long versionCount, VersionResponse latestVersion, long revision) {
        public static ApplicationResponse from(ApplicationEntity a, long versionCount, VersionResponse latestVersion) {
            return new ApplicationResponse(a.getId(), a.getAppCode(), a.getAppType(), a.getStatus(),
                    a.isFirstParty(), a.isKillSwitchActive(), a.getPartnerId(), versionCount, latestVersion, a.getRevision());
        }
    }
}
```

- [ ] **Step 4: Write `VersionService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.VersionDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.AppCategoryRepository;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionService {
    private final AppVersionRepository versions;
    private final ApplicationRepository applications;
    private final AppCategoryRepository categories;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public VersionService(AppVersionRepository versions, ApplicationRepository applications,
                          AppCategoryRepository categories, CurrentUser currentUser, AuditService audit) {
        this.versions = versions;
        this.applications = applications;
        this.categories = categories;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public VersionResponse createVersion(UUID appId, VersionMetadataFields fields) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var entity = createVersionEntity(app.getId(), app.getPartnerId(), nextVersionCode(appId), fields);
        audit.log(app.getPartnerId(), "VERSION_CREATED", "APP_VERSION", entity.getId(), null, VersionResponse.from(entity));
        return VersionResponse.from(entity);
    }

    @Transactional
    AppVersionEntity createInitialVersion(UUID appId, UUID partnerId, VersionMetadataFields fields) {
        return createVersionEntity(appId, partnerId, 1, fields);
    }

    @Transactional(readOnly = true)
    public Page<VersionResponse> listVersions(UUID appId, VersionStatus status, Pageable pageable) {
        requireApp(appId);
        var page = status != null ? versions.findByAppIdAndStatus(appId, status, pageable) : versions.findByAppId(appId, pageable);
        return page.map(VersionResponse::from);
    }

    @Transactional(readOnly = true)
    public VersionResponse getVersion(UUID appId, UUID versionId) {
        return VersionResponse.from(requireVersion(appId, versionId));
    }

    @Transactional
    public VersionResponse updateVersion(UUID appId, UUID versionId, UpdateVersionRequest r) {
        var v = requireVersion(appId, versionId);
        var before = VersionResponse.from(v);
        v.assertEditable();
        v.updateMetadata(r.displayName(), r.descriptionShort(), r.descriptionLong(), r.supportedLanguages());
        v.replaceCategories(resolveCategories(r.categoryCodes()));
        audit.log(v.getPartnerId(), "VERSION_UPDATED", "APP_VERSION", v.getId(), before, VersionResponse.from(v));
        return VersionResponse.from(v);
    }

    public AppVersionEntity requireVersion(UUID appId, UUID versionId) {
        var v = versions.findById(versionId).orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND));
        if (!v.getAppId().equals(appId)) throw new BusinessException(ErrorCode.VERSION_NOT_FOUND);
        return v;
    }

    private void requireApp(UUID appId) {
        if (!applications.existsById(appId)) throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
    }

    private AppVersionEntity createVersionEntity(UUID appId, UUID partnerId, int versionCode, VersionMetadataFields fields) {
        var entity = AppVersionEntity.create(appId, partnerId, versionCode, fields.versionName(), fields.displayName(),
                fields.packageName(), fields.descriptionShort(), fields.descriptionLong(), fields.supportedLanguages());
        entity.replaceCategories(resolveCategories(fields.categoryCodes()));
        return versions.save(entity);
    }

    private int nextVersionCode(UUID appId) {
        return versions.findTopByAppIdOrderByVersionCodeDesc(appId).map(v -> v.getVersionCode() + 1).orElse(1);
    }

    private Set<AppCategoryEntity> resolveCategories(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return Set.of();
        var found = categories.findByCodeIn(codes);
        if (found.size() != codes.size()) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        return new HashSet<>(found);
    }
}
```

- [ ] **Step 5: Write `ApplicationService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ApplicationDtos.*;
import com.vnpt.mac.applications.dto.VersionDtos.VersionResponse;
import com.vnpt.mac.applications.entity.ApplicationEntity;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.repository.AppVersionRepository;
import com.vnpt.mac.applications.repository.ApplicationRepository;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private final ApplicationRepository applications;
    private final AppVersionRepository versions;
    private final VersionService versionService;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ApplicationService(ApplicationRepository applications, AppVersionRepository versions,
                              VersionService versionService, CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versions = versions;
        this.versionService = versionService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        var partnerId = currentUser.partnerId();
        var app = ApplicationEntity.create(partnerId, request.appType());
        applications.save(app);
        var version = versionService.createInitialVersion(app.getId(), partnerId, request.version());
        var response = ApplicationResponse.from(app, 1, VersionResponse.from(version));
        audit.log(partnerId, "APPLICATION_CREATED", "APPLICATION", app.getId(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> list(ApplicationStatus status, ApplicationType appType, Pageable pageable) {
        var principal = currentUser.require();
        boolean global = principal.authorities().stream().anyMatch(a -> a.getAuthority().equals("app.read.all"));
        UUID scopePartnerId = global ? null : principal.partnerId();
        return applications.search(scopePartnerId, status, appType, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id) {
        return toResponse(require(id));
    }

    public ApplicationEntity require(UUID id) {
        return applications.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private ApplicationResponse toResponse(ApplicationEntity app) {
        long versionCount = versions.countByAppId(app.getId());
        var latest = versions.findTopByAppIdOrderByVersionCodeDesc(app.getId()).map(VersionResponse::from).orElse(null);
        return ApplicationResponse.from(app, versionCount, latest);
    }
}
```

- [ ] **Step 6: Write `ApplicationController.java`**

```java
package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ApplicationDtos.*;
import com.vnpt.mac.applications.entity.ApplicationStatus;
import com.vnpt.mac.applications.entity.ApplicationType;
import com.vnpt.mac.applications.service.ApplicationService;
import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Application", description = "Quản lý Application: danh sách, tạo (wizard), chi tiết")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('app.read.all') or hasAuthority('app.read')")
    @Operation(summary = "Danh sách application", description = "Lọc theo status/appType. Partner Admin/Dev chỉ thấy app của partner mình trừ khi có app.read.all.")
    public ApiResponse<PageResponse<ApplicationResponse>> list(@RequestParam(required = false) ApplicationStatus status,
                                                               @RequestParam(required = false) ApplicationType appType,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.list(status, appType, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('app.create')")
    @Operation(summary = "Tạo Application (wizard)", description = "Tạo App shell và version 1 (DRAFT) trong 1 transaction. Cần quyền app.create.")
    public ApiResponse<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest r) {
        return ApiResponse.success(service.create(r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('app.read') and @resourceAuth.app(#id)")
    @Operation(summary = "Chi tiết application", description = "Cần quyền app.read trên app thuộc partner/assignment của mình.")
    public ApiResponse<ApplicationResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run: `mvn -q compile`
Expected: no output, exit code 0 (compile errors would print to stderr and fail the build).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/vnpt/mac/applications/repository/ApplicationRepository.java \
        src/main/java/com/vnpt/mac/applications/dto/VersionDtos.java \
        src/main/java/com/vnpt/mac/applications/dto/ApplicationDtos.java \
        src/main/java/com/vnpt/mac/applications/service/VersionService.java \
        src/main/java/com/vnpt/mac/applications/service/ApplicationService.java \
        src/main/java/com/vnpt/mac/applications/controller/ApplicationController.java
git commit -m "feat(applications): application list, create wizard, and detail endpoints"
```

---

### Task 5: Version list/detail/update endpoints (`VersionController`)

**Files:**
- Create: `src/main/java/com/vnpt/mac/applications/controller/VersionController.java`

**Interfaces:**
- Consumes: `VersionService.listVersions/getVersion/createVersion/updateVersion` (Task 4), `VersionDtos.VersionMetadataFields/UpdateVersionRequest/VersionResponse` (Task 4).

- [ ] **Step 1: Write `VersionController.java`**

```java
package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.VersionDtos.*;
import com.vnpt.mac.applications.entity.VersionStatus;
import com.vnpt.mac.applications.service.VersionService;
import com.vnpt.mac.common.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions")
@Tag(name = "Version", description = "Quản lý version của 1 application")
public class VersionController {
    private final VersionService service;

    public VersionController(VersionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Danh sách version", description = "Lọc theo status. Cần quyền version.read.")
    public ApiResponse<PageResponse<VersionResponse>> list(@PathVariable UUID appId,
                                                            @RequestParam(required = false) VersionStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(service.listVersions(appId, status, PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('version.create') and @resourceAuth.app(#appId)")
    @Operation(summary = "Tạo version mới", description = "version_code tự tăng. Cần quyền version.create.")
    public ApiResponse<VersionResponse> create(@PathVariable UUID appId, @Valid @RequestBody VersionMetadataFields r) {
        return ApiResponse.success(service.createVersion(appId, r));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Chi tiết version")
    public ApiResponse<VersionResponse> get(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.getVersion(appId, versionId));
    }

    @PatchMapping("/{versionId}")
    @PreAuthorize("hasAuthority('version.update') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cập nhật metadata version", description = "Chỉ cho phép khi version ở DRAFT hoặc CHANGES_REQUESTED.")
    public ApiResponse<VersionResponse> update(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody UpdateVersionRequest r) {
        return ApiResponse.success(service.updateVersion(appId, versionId, r));
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q compile`
Expected: exit code 0, no output.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/vnpt/mac/applications/controller/VersionController.java
git commit -m "feat(applications): version list/detail/create/update endpoints"
```

---

### Task 6: Local filesystem artifact storage

**Files:**
- Create: `src/main/java/com/vnpt/mac/config/StorageProperties.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/vnpt/mac/applications/service/ArtifactStorageService.java`
- Test: `src/test/java/com/vnpt/mac/applications/service/ArtifactStorageServiceTest.java`

**Interfaces:**
- Produces: `StorageProperties(String artifactsDir, long maxArtifactBytes)`, bound from `mac.storage.*` (auto-registered — `MacApplication` already has `@ConfigurationPropertiesScan`).
- Produces: `ArtifactStorageService.StoredArtifact(String storageKey, long sizeBytes, String checksumSha256)`, `ArtifactStorageService.store(UUID versionId, String originalFilename, byte[] content)`, `.delete(String storageKey)`. Consumed by `ArtifactService` in Task 8.

- [ ] **Step 1: Write `StorageProperties.java`**

```java
package com.vnpt.mac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mac.storage")
public record StorageProperties(String artifactsDir, long maxArtifactBytes) {}
```

- [ ] **Step 2: Add storage + multipart config to `application.yml`**

In `src/main/resources/application.yml`, add a `multipart` block under the existing `spring.servlet` section — the file currently has no `spring.servlet` key, so add it as a new child of the top-level `spring:` block (alongside `application`, `datasource`, `jpa`, `flyway`, `jackson`):
```yaml
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
```
And add a new top-level `mac.storage` block alongside the existing `mac.security`/`mac.invitation`/`mac.api-token`/`mac.bootstrap-admin` blocks:
```yaml
  storage:
    artifacts-dir: ${ARTIFACTS_DIR:./data/artifacts}
    max-artifact-bytes: ${MAX_ARTIFACT_BYTES:104857600}
```

- [ ] **Step 3: Write `ArtifactStorageService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.config.StorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ArtifactStorageService {
    private final StorageProperties properties;

    public ArtifactStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    public record StoredArtifact(String storageKey, long sizeBytes, String checksumSha256) {}

    public StoredArtifact store(UUID versionId, String originalFilename, byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var checksum = HexFormat.of().formatHex(digest.digest(content));
            var relativeKey = versionId + "/" + UUID.randomUUID() + "-" + sanitize(originalFilename);
            var target = Path.of(properties.artifactsDir()).resolve(relativeKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return new StoredArtifact(relativeKey, content.length, checksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Không lưu được artifact", e);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(Path.of(properties.artifactsDir()).resolve(storageKey));
        } catch (IOException ignored) {
        }
    }

    private String sanitize(String filename) {
        return filename == null ? "artifact" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
```

- [ ] **Step 4: Write the failing test first — `ArtifactStorageServiceTest.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ArtifactStorageServiceTest {
    @TempDir Path tempDir;

    @Test void storesFileAndComputesChecksum() throws Exception {
        var service = new ArtifactStorageService(new StorageProperties(tempDir.toString(), 1_000_000L));
        var content = "hello world".getBytes(StandardCharsets.UTF_8);
        var versionId = UUID.randomUUID();

        var stored = service.store(versionId, "app.zip", content);

        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.checksumSha256()).hasSize(64);
        assertThat(Files.readAllBytes(tempDir.resolve(stored.storageKey()))).isEqualTo(content);
    }

    @Test void deleteRemovesTheStoredFile() {
        var service = new ArtifactStorageService(new StorageProperties(tempDir.toString(), 1_000_000L));
        var stored = service.store(UUID.randomUUID(), "app.zip", "data".getBytes(StandardCharsets.UTF_8));

        service.delete(stored.storageKey());

        assertThat(Files.exists(tempDir.resolve(stored.storageKey()))).isFalse();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn test -Dtest=ArtifactStorageServiceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/vnpt/mac/config/StorageProperties.java \
        src/main/resources/application.yml \
        src/main/java/com/vnpt/mac/applications/service/ArtifactStorageService.java \
        src/test/java/com/vnpt/mac/applications/service/ArtifactStorageServiceTest.java
git commit -m "feat(applications): local filesystem artifact storage"
```

---

### Task 7: Manifest validation logic (`ManifestValidationService`)

**Files:**
- Create: `src/main/java/com/vnpt/mac/applications/entity/FindingSeverity.java`
- Create: `src/main/java/com/vnpt/mac/applications/service/ManifestValidationService.java`
- Test: `src/test/java/com/vnpt/mac/applications/service/ManifestValidationServiceTest.java`

**Interfaces:**
- Produces: `FindingSeverity{ERROR, WARNING, INFO}`.
- Produces: `ManifestValidationService.Finding(String ruleCode, FindingSeverity severity, String message)`, `ManifestValidationService.ValidationOutcome(boolean passed, List<Finding> findings)`, and methods `validateMiniApp(byte[] zipBytes, long maxBytes)`, `validateFeatureApp(String filename, long sizeBytes, long maxBytes)`, `validateWebapp(String destinationUrl)`, `validateModule(String moduleNamespace)` — all consumed by `ArtifactService` in Task 8.

- [ ] **Step 1: Write `FindingSeverity.java`**

```java
package com.vnpt.mac.applications.entity;

public enum FindingSeverity {
    ERROR,
    WARNING,
    INFO
}
```

- [ ] **Step 2: Write `ManifestValidationService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.entity.FindingSeverity;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;

@Service
public class ManifestValidationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Finding(String ruleCode, FindingSeverity severity, String message) {}

    public record ValidationOutcome(boolean passed, List<Finding> findings) {
        static ValidationOutcome of(List<Finding> findings) {
            boolean passed = findings.stream().noneMatch(f -> f.severity() == FindingSeverity.ERROR);
            return new ValidationOutcome(passed, findings);
        }
    }

    public ValidationOutcome validateMiniApp(byte[] zipBytes, long maxBytes) {
        var findings = new ArrayList<Finding>();
        if (zipBytes.length > maxBytes) {
            findings.add(new Finding("ARTIFACT_TOO_LARGE", FindingSeverity.ERROR,
                    "Kích thước " + zipBytes.length + " vượt giới hạn " + maxBytes + " bytes"));
        }
        boolean hasManifest = false;
        boolean hasIndexHtml = false;
        boolean manifestValidJson = false;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("manifest.json")) {
                    hasManifest = true;
                    byte[] manifestBytes = zip.readAllBytes();
                    try {
                        var node = MAPPER.readTree(manifestBytes);
                        manifestValidJson = node != null && node.isObject();
                    } catch (Exception e) {
                        manifestValidJson = false;
                    }
                } else if (entry.getName().equals("index.html")) {
                    hasIndexHtml = true;
                }
            }
        } catch (IOException e) {
            findings.add(new Finding("ARTIFACT_NOT_A_ZIP", FindingSeverity.ERROR, "File không phải ZIP hợp lệ"));
            return ValidationOutcome.of(findings);
        }
        if (!hasManifest) findings.add(new Finding("MANIFEST_MISSING", FindingSeverity.ERROR, "Thiếu manifest.json ở gốc ZIP"));
        else if (!manifestValidJson) findings.add(new Finding("MANIFEST_INVALID_JSON", FindingSeverity.ERROR, "manifest.json không phải JSON object hợp lệ"));
        if (!hasIndexHtml) findings.add(new Finding("INDEX_HTML_MISSING", FindingSeverity.ERROR, "Thiếu index.html ở gốc ZIP"));
        if (findings.isEmpty()) findings.add(new Finding("MINIAPP_VALIDATION_OK", FindingSeverity.INFO, "Manifest và index.html hợp lệ"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateFeatureApp(String filename, long sizeBytes, long maxBytes) {
        var findings = new ArrayList<Finding>();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".apk") && !lower.endsWith(".aab"))
            findings.add(new Finding("INVALID_EXTENSION", FindingSeverity.ERROR, "Yêu cầu file .apk hoặc .aab"));
        if (sizeBytes > maxBytes)
            findings.add(new Finding("ARTIFACT_TOO_LARGE", FindingSeverity.ERROR, "Kích thước vượt giới hạn " + maxBytes + " bytes"));
        findings.add(new Finding("SIGNATURE_CHECK_DEFERRED", FindingSeverity.INFO,
                "Xác minh chữ ký số chưa được triển khai ở milestone này"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateWebapp(String destinationUrl) {
        var findings = new ArrayList<Finding>();
        boolean valid = destinationUrl != null && destinationUrl.matches("^https://[\\w.-]+(:\\d+)?(/.*)?$");
        if (!valid) findings.add(new Finding("INVALID_DESTINATION_URL", FindingSeverity.ERROR,
                "destinationUrl phải bắt đầu bằng https:// và đúng định dạng URL"));
        else findings.add(new Finding("WEBAPP_URL_OK", FindingSeverity.INFO, "URL hợp lệ (chưa kiểm tra SSL/health thực tế)"));
        return ValidationOutcome.of(findings);
    }

    public ValidationOutcome validateModule(String moduleNamespace) {
        var findings = new ArrayList<Finding>();
        if (moduleNamespace == null || moduleNamespace.isBlank())
            findings.add(new Finding("MODULE_NAMESPACE_REQUIRED", FindingSeverity.ERROR, "moduleNamespace không được để trống"));
        else findings.add(new Finding("MODULE_NAMESPACE_OK", FindingSeverity.INFO, "moduleNamespace hợp lệ"));
        return ValidationOutcome.of(findings);
    }
}
```

- [ ] **Step 3: Write the failing test first — `ManifestValidationServiceTest.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.entity.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

class ManifestValidationServiceTest {
    private final ManifestValidationService service = new ManifestValidationService();

    private byte[] zipOf(String... nameAndContentPairs) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < nameAndContentPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameAndContentPairs[i]));
                zip.write(nameAndContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test void validMiniAppZipPasses() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}", "index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isTrue();
    }

    @Test void missingManifestFailsWithError() throws IOException {
        var zip = zipOf("index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("MANIFEST_MISSING") && f.severity() == FindingSeverity.ERROR);
    }

    @Test void missingIndexHtmlFailsWithError() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}");
        var outcome = service.validateMiniApp(zip, 10_000_000);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("INDEX_HTML_MISSING"));
    }

    @Test void oversizeZipFailsWithError() throws IOException {
        var zip = zipOf("manifest.json", "{\"name\":\"demo\"}", "index.html", "<html></html>");
        var outcome = service.validateMiniApp(zip, 5);
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.findings()).anyMatch(f -> f.ruleCode().equals("ARTIFACT_TOO_LARGE"));
    }

    @Test void webappRequiresHttpsUrl() {
        assertThat(service.validateWebapp("http://insecure.example.com").passed()).isFalse();
        assertThat(service.validateWebapp("https://secure.example.com").passed()).isTrue();
    }

    @Test void moduleRequiresNonBlankNamespace() {
        assertThat(service.validateModule("  ").passed()).isFalse();
        assertThat(service.validateModule("com.vnpt.module").passed()).isTrue();
    }

    @Test void featureAppRequiresApkOrAabExtension() {
        assertThat(service.validateFeatureApp("app.exe", 100, 10_000_000).passed()).isFalse();
        assertThat(service.validateFeatureApp("app.apk", 100, 10_000_000).passed()).isTrue();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=ManifestValidationServiceTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vnpt/mac/applications/entity/FindingSeverity.java \
        src/main/java/com/vnpt/mac/applications/service/ManifestValidationService.java \
        src/test/java/com/vnpt/mac/applications/service/ManifestValidationServiceTest.java
git commit -m "feat(applications): manifest validation logic for all app types"
```

---

### Task 8: Artifact upload, config, and validation endpoints (`ArtifactService`, `ArtifactController`)

**Files:**
- Create: `src/main/java/com/vnpt/mac/applications/entity/ArtifactKind.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ValidationStatus.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/VersionArtifactEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/VersionWebappConfigEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/VersionModuleConfigEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ValidationRunEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ValidationFindingEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/VersionArtifactRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/VersionWebappConfigRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/VersionModuleConfigRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/ValidationRunRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/ValidationFindingRepository.java`
- Modify: `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`
- Create: `src/main/java/com/vnpt/mac/applications/dto/ArtifactDtos.java`
- Create: `src/main/java/com/vnpt/mac/applications/service/ArtifactService.java`
- Create: `src/main/java/com/vnpt/mac/applications/controller/ArtifactController.java`

**Interfaces:**
- Consumes: `VersionService.requireVersion` (Task 4), `ApplicationRepository` (Task 2/4), `ArtifactStorageService.store/delete` (Task 6), `ManifestValidationService.validate*`/`ValidationOutcome` (Task 7).
- Produces: `ArtifactDtos.ArtifactResponse/WebappConfigRequest/WebappConfigResponse/ModuleConfigRequest/ModuleConfigResponse/FindingResponse/ValidationRunResponse`. Consumed by `ArtifactController` here and referenced nowhere else.
- Adds to `ErrorCode`: `ARTIFACT_TYPE_MISMATCH(400)`, `ARTIFACT_MISSING(400)`, `VALIDATION_FAILED(409)` (the latter two are also used by `ReviewService` in Task 9).

- [ ] **Step 1: Add the new error codes**

In `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`, add:
```java
    ARTIFACT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
    ARTIFACT_MISSING(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.CONFLICT),
```

- [ ] **Step 2: Write the two new enums**

```java
package com.vnpt.mac.applications.entity;

public enum ArtifactKind {
    ZIP,
    APK,
    AAB
}
```

```java
package com.vnpt.mac.applications.entity;

public enum ValidationStatus {
    RUNNING,
    PASSED,
    FAILED
}
```

- [ ] **Step 3: Write the artifact/config entities**

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_artifacts")
public class VersionArtifactEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArtifactKind kind;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "signature_fingerprint")
    private String signatureFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VersionArtifactEntity() {}

    public static VersionArtifactEntity create(UUID versionId, ArtifactKind kind, String storageKey,
                                                String originalFilename, long sizeBytes, String checksumSha256,
                                                String signatureFingerprint) {
        var entity = new VersionArtifactEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.kind = kind;
        entity.storageKey = storageKey;
        entity.originalFilename = originalFilename;
        entity.sizeBytes = sizeBytes;
        entity.checksumSha256 = checksumSha256;
        entity.signatureFingerprint = signatureFingerprint;
        entity.createdAt = Instant.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public ArtifactKind getKind() { return kind; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getSignatureFingerprint() { return signatureFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
```

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_webapp_config")
public class VersionWebappConfigEntity {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "destination_url", nullable = false, length = 500)
    private String destinationUrl;

    @Column(name = "ssl_valid", nullable = false)
    private boolean sslValid;

    @Column(name = "last_health_status")
    private Integer lastHealthStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    protected VersionWebappConfigEntity() {}

    public static VersionWebappConfigEntity create(UUID versionId, String destinationUrl, boolean sslValid) {
        var entity = new VersionWebappConfigEntity();
        entity.versionId = versionId;
        entity.destinationUrl = destinationUrl;
        entity.sslValid = sslValid;
        return entity;
    }

    public void update(String destinationUrl, boolean sslValid) {
        this.destinationUrl = destinationUrl;
        this.sslValid = sslValid;
    }

    public UUID getVersionId() { return versionId; }
    public String getDestinationUrl() { return destinationUrl; }
    public boolean isSslValid() { return sslValid; }
    public Integer getLastHealthStatus() { return lastHealthStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
}
```

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "version_module_config")
public class VersionModuleConfigEntity {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "module_namespace", nullable = false)
    private String moduleNamespace;

    @Column
    private String description;

    protected VersionModuleConfigEntity() {}

    public static VersionModuleConfigEntity create(UUID versionId, String moduleNamespace, String description) {
        var entity = new VersionModuleConfigEntity();
        entity.versionId = versionId;
        entity.moduleNamespace = moduleNamespace;
        entity.description = description;
        return entity;
    }

    public void update(String moduleNamespace, String description) {
        this.moduleNamespace = moduleNamespace;
        this.description = description;
    }

    public UUID getVersionId() { return versionId; }
    public String getModuleNamespace() { return moduleNamespace; }
    public String getDescription() { return description; }
}
```

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_runs")
public class ValidationRunEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus status;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ValidationRunEntity() {}

    public static ValidationRunEntity start(UUID versionId, UUID triggeredBy) {
        var entity = new ValidationRunEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.status = ValidationStatus.RUNNING;
        entity.triggeredBy = triggeredBy;
        entity.startedAt = Instant.now();
        return entity;
    }

    public void complete(boolean passed) {
        status = passed ? ValidationStatus.PASSED : ValidationStatus.FAILED;
        completedAt = Instant.now();
    }

    public boolean passed() { return status == ValidationStatus.PASSED; }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public ValidationStatus getStatus() { return status; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
```

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "validation_findings")
public class ValidationFindingEntity {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FindingSeverity severity;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> context = Map.of();

    protected ValidationFindingEntity() {}

    public static ValidationFindingEntity create(UUID runId, String ruleCode, FindingSeverity severity, String message, Map<String, Object> context) {
        var entity = new ValidationFindingEntity();
        entity.id = UUID.randomUUID();
        entity.runId = runId;
        entity.ruleCode = ruleCode;
        entity.severity = severity;
        entity.message = message;
        entity.context = context == null ? Map.of() : context;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getRuleCode() { return ruleCode; }
    public FindingSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Map<String, Object> getContext() { return context; }
}
```

- [ ] **Step 4: Write the five repositories**

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionArtifactEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionArtifactRepository extends JpaRepository<VersionArtifactEntity, UUID> {
    Optional<VersionArtifactEntity> findByVersionId(UUID versionId);
}
```

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionWebappConfigEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionWebappConfigRepository extends JpaRepository<VersionWebappConfigEntity, UUID> {
}
```

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.VersionModuleConfigEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VersionModuleConfigRepository extends JpaRepository<VersionModuleConfigEntity, UUID> {
}
```

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ValidationRunEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ValidationRunRepository extends JpaRepository<ValidationRunEntity, UUID> {
    Optional<ValidationRunEntity> findTopByVersionIdOrderByStartedAtDesc(UUID versionId);
}
```

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ValidationFindingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ValidationFindingRepository extends JpaRepository<ValidationFindingEntity, UUID> {
    List<ValidationFindingEntity> findByRunId(UUID runId);
}
```

- [ ] **Step 5: Write `ArtifactDtos.java`**

```java
package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArtifactDtos {
    private ArtifactDtos() {}

    public record ArtifactResponse(UUID id, UUID versionId, ArtifactKind kind, String originalFilename,
                                   long sizeBytes, String checksumSha256, String signatureFingerprint, Instant createdAt) {
        public static ArtifactResponse from(VersionArtifactEntity e) {
            return new ArtifactResponse(e.getId(), e.getVersionId(), e.getKind(), e.getOriginalFilename(),
                    e.getSizeBytes(), e.getChecksumSha256(), e.getSignatureFingerprint(), e.getCreatedAt());
        }
    }

    public record WebappConfigRequest(@NotBlank @Size(max = 500) String destinationUrl) {
    }

    public record WebappConfigResponse(UUID versionId, String destinationUrl, boolean sslValid,
                                       Integer lastHealthStatus, Instant lastCheckedAt) {
        public static WebappConfigResponse from(VersionWebappConfigEntity e) {
            return new WebappConfigResponse(e.getVersionId(), e.getDestinationUrl(), e.isSslValid(),
                    e.getLastHealthStatus(), e.getLastCheckedAt());
        }
    }

    public record ModuleConfigRequest(@NotBlank @Size(max = 255) String moduleNamespace, String description) {
    }

    public record ModuleConfigResponse(UUID versionId, String moduleNamespace, String description) {
        public static ModuleConfigResponse from(VersionModuleConfigEntity e) {
            return new ModuleConfigResponse(e.getVersionId(), e.getModuleNamespace(), e.getDescription());
        }
    }

    public record FindingResponse(UUID id, String ruleCode, FindingSeverity severity, String message, Map<String, Object> context) {
        public static FindingResponse from(ValidationFindingEntity e) {
            return new FindingResponse(e.getId(), e.getRuleCode(), e.getSeverity(), e.getMessage(), e.getContext());
        }
    }

    public record ValidationRunResponse(UUID id, UUID versionId, ValidationStatus status, Instant startedAt,
                                        Instant completedAt, List<FindingResponse> findings) {
        public static ValidationRunResponse from(ValidationRunEntity run, List<ValidationFindingEntity> findings) {
            return new ValidationRunResponse(run.getId(), run.getVersionId(), run.getStatus(), run.getStartedAt(),
                    run.getCompletedAt(), findings.stream().map(FindingResponse::from).toList());
        }

        public static ValidationRunResponse empty(UUID versionId) {
            return new ValidationRunResponse(null, versionId, null, null, null, List.of());
        }
    }
}
```

- [ ] **Step 6: Write `ArtifactService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ArtifactDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.config.StorageProperties;
import com.vnpt.mac.security.CurrentUser;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtifactService {
    private final ApplicationRepository applications;
    private final VersionService versionService;
    private final VersionArtifactRepository artifacts;
    private final VersionWebappConfigRepository webappConfigs;
    private final VersionModuleConfigRepository moduleConfigs;
    private final ValidationRunRepository runs;
    private final ValidationFindingRepository findings;
    private final ArtifactStorageService storage;
    private final ManifestValidationService validation;
    private final StorageProperties storageProperties;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ArtifactService(ApplicationRepository applications, VersionService versionService,
                           VersionArtifactRepository artifacts, VersionWebappConfigRepository webappConfigs,
                           VersionModuleConfigRepository moduleConfigs, ValidationRunRepository runs,
                           ValidationFindingRepository findings, ArtifactStorageService storage,
                           ManifestValidationService validation, StorageProperties storageProperties,
                           CurrentUser currentUser, AuditService audit) {
        this.applications = applications;
        this.versionService = versionService;
        this.artifacts = artifacts;
        this.webappConfigs = webappConfigs;
        this.moduleConfigs = moduleConfigs;
        this.runs = runs;
        this.findings = findings;
        this.storage = storage;
        this.validation = validation;
        this.storageProperties = storageProperties;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ArtifactResponse uploadArtifact(UUID appId, UUID versionId, MultipartFile file) {
        var app = requireApp(appId);
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Không đọc được file upload", e);
        }
        ArtifactKind kind;
        ManifestValidationService.ValidationOutcome outcome;
        if (app.getAppType() == ApplicationType.MINIAPP) {
            kind = ArtifactKind.ZIP;
            outcome = validation.validateMiniApp(content, storageProperties.maxArtifactBytes());
        } else if (app.getAppType() == ApplicationType.FEATURE_APP) {
            kind = resolveFeatureAppKind(file.getOriginalFilename());
            outcome = validation.validateFeatureApp(file.getOriginalFilename(), content.length, storageProperties.maxArtifactBytes());
        } else {
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH,
                    "App type " + app.getAppType() + " không nhận artifact qua endpoint này");
        }
        artifacts.findByVersionId(versionId).ifPresent(existing -> {
            storage.delete(existing.getStorageKey());
            artifacts.delete(existing);
        });
        var stored = storage.store(versionId, file.getOriginalFilename(), content);
        var entity = artifacts.save(VersionArtifactEntity.create(versionId, kind, stored.storageKey(),
                file.getOriginalFilename(), stored.sizeBytes(), stored.checksumSha256(), null));
        recordValidationRun(versionId, outcome);
        var response = ArtifactResponse.from(entity);
        audit.log(version.getPartnerId(), "ARTIFACT_UPLOADED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public WebappConfigResponse setWebappConfig(UUID appId, UUID versionId, WebappConfigRequest r) {
        var app = requireApp(appId);
        if (app.getAppType() != ApplicationType.WEBAPP)
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Chỉ WebApp mới cấu hình destination URL");
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var outcome = validation.validateWebapp(r.destinationUrl());
        var existing = webappConfigs.findById(versionId).orElse(null);
        VersionWebappConfigEntity config;
        if (existing != null) {
            existing.update(r.destinationUrl(), outcome.passed());
            config = existing;
        } else {
            config = VersionWebappConfigEntity.create(versionId, r.destinationUrl(), outcome.passed());
        }
        webappConfigs.save(config);
        recordValidationRun(versionId, outcome);
        var response = WebappConfigResponse.from(config);
        audit.log(version.getPartnerId(), "WEBAPP_CONFIG_SET", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public ModuleConfigResponse setModuleConfig(UUID appId, UUID versionId, ModuleConfigRequest r) {
        var app = requireApp(appId);
        if (app.getAppType() != ApplicationType.APP_MODULE)
            throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Chỉ App Module mới cấu hình module metadata");
        var version = versionService.requireVersion(appId, versionId);
        version.assertEditable();
        var outcome = validation.validateModule(r.moduleNamespace());
        var existing = moduleConfigs.findById(versionId).orElse(null);
        VersionModuleConfigEntity config;
        if (existing != null) {
            existing.update(r.moduleNamespace(), r.description());
            config = existing;
        } else {
            config = VersionModuleConfigEntity.create(versionId, r.moduleNamespace(), r.description());
        }
        moduleConfigs.save(config);
        recordValidationRun(versionId, outcome);
        var response = ModuleConfigResponse.from(config);
        audit.log(version.getPartnerId(), "MODULE_CONFIG_SET", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ValidationRunResponse latestValidation(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        var run = runs.findTopByVersionIdOrderByStartedAtDesc(versionId).orElse(null);
        if (run == null) return ValidationRunResponse.empty(versionId);
        return ValidationRunResponse.from(run, findings.findByRunId(run.getId()));
    }

    private void recordValidationRun(UUID versionId, ManifestValidationService.ValidationOutcome outcome) {
        var run = ValidationRunEntity.start(versionId, currentUser.id());
        run.complete(outcome.passed());
        runs.save(run);
        outcome.findings().forEach(f -> findings.save(
                ValidationFindingEntity.create(run.getId(), f.ruleCode(), f.severity(), f.message(), Map.of())));
    }

    private ArtifactKind resolveFeatureAppKind(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".apk")) return ArtifactKind.APK;
        if (lower.endsWith(".aab")) return ArtifactKind.AAB;
        throw new BusinessException(ErrorCode.ARTIFACT_TYPE_MISMATCH, "Feature App yêu cầu file .apk hoặc .aab");
    }

    private ApplicationEntity requireApp(UUID appId) {
        return applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
    }
}
```

- [ ] **Step 7: Write `ArtifactController.java`**

```java
package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ArtifactDtos.*;
import com.vnpt.mac.applications.service.ArtifactService;
import com.vnpt.mac.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions/{versionId}")
@Tag(name = "Artifact", description = "Upload artifact/cấu hình theo app type và validate manifest")
public class ArtifactController {
    private final ArtifactService service;

    public ArtifactController(ArtifactService service) {
        this.service = service;
    }

    @PostMapping(value = "/artifact", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Upload artifact (ZIP/APK/AAB)", description = "MiniApp nhận ZIP, Feature App nhận APK/AAB. Chạy validate ngay sau khi lưu.")
    public ApiResponse<ArtifactResponse> upload(@PathVariable UUID appId, @PathVariable UUID versionId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(service.uploadArtifact(appId, versionId, file));
    }

    @PutMapping("/webapp-config")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cấu hình WebApp destination URL")
    public ApiResponse<WebappConfigResponse> webappConfig(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody WebappConfigRequest r) {
        return ApiResponse.success(service.setWebappConfig(appId, versionId, r));
    }

    @PutMapping("/module-config")
    @PreAuthorize("hasAuthority('artifact.upload') and @resourceAuth.app(#appId)")
    @Operation(summary = "Cấu hình App Module metadata")
    public ApiResponse<ModuleConfigResponse> moduleConfig(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody ModuleConfigRequest r) {
        return ApiResponse.success(service.setModuleConfig(appId, versionId, r));
    }

    @GetMapping("/validation")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Kết quả validate mới nhất")
    public ApiResponse<ValidationRunResponse> validation(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.latestValidation(appId, versionId));
    }
}
```

- [ ] **Step 8: Verify it compiles**

Run: `mvn -q compile`
Expected: exit code 0, no output.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/vnpt/mac/applications/entity/ArtifactKind.java \
        src/main/java/com/vnpt/mac/applications/entity/ValidationStatus.java \
        src/main/java/com/vnpt/mac/applications/entity/VersionArtifactEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/VersionWebappConfigEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/VersionModuleConfigEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/ValidationRunEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/ValidationFindingEntity.java \
        src/main/java/com/vnpt/mac/applications/repository/VersionArtifactRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/VersionWebappConfigRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/VersionModuleConfigRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/ValidationRunRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/ValidationFindingRepository.java \
        src/main/java/com/vnpt/mac/common/exception/ErrorCode.java \
        src/main/java/com/vnpt/mac/applications/dto/ArtifactDtos.java \
        src/main/java/com/vnpt/mac/applications/service/ArtifactService.java \
        src/main/java/com/vnpt/mac/applications/controller/ArtifactController.java
git commit -m "feat(applications): artifact upload, webapp/module config, and validation endpoints"
```

---

### Task 9: Review workflow (`ReviewService`, `ReviewController`) + Reviewer resource-scoping fix

**Files:**
- Create: `src/main/java/com/vnpt/mac/applications/entity/SubmissionStatus.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ReviewDecisionType.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ReviewSubmissionEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/entity/ReviewDecisionEntity.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/ReviewSubmissionRepository.java`
- Create: `src/main/java/com/vnpt/mac/applications/repository/ReviewDecisionRepository.java`
- Modify: `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`
- Create: `src/main/java/com/vnpt/mac/applications/dto/ReviewDtos.java`
- Create: `src/main/java/com/vnpt/mac/applications/service/ReviewService.java`
- Create: `src/main/java/com/vnpt/mac/applications/controller/ReviewController.java`
- Modify: `src/main/java/com/vnpt/mac/security/ResourceAuthorizationService.java`

**Interfaces:**
- Consumes: `VersionService.requireVersion`, `ApplicationRepository`, `AppVersionRepository`, `ValidationRunRepository.findTopByVersionIdOrderByStartedAtDesc` (Task 8).
- Produces: `ReviewDtos.ReviewDecisionRequest(decision, feedback)`, `ReviewDtos.ReviewDecisionResponse.from(ReviewDecisionEntity)`, `ReviewDtos.ReviewSubmissionResponse.from(ReviewSubmissionEntity, ReviewDecisionResponse)`.
- Adds to `ErrorCode`: `REVIEW_FEEDBACK_REQUIRED(400)`.

**Important fix bundled into this task:** `ResourceAuthorizationService.app(UUID)` currently returns `true` only for `PLATFORM_ADMIN`, `ADMIN`, the owning `PARTNER_ADMIN`, or an assigned `PARTNER_DEVELOPER`. Per the design doc's M2 module matrix, `REVIEWER` has read-only access across submissions (it has no `partner_id` and isn't an app-assignment). Without this fix, a `REVIEWER` would be incorrectly `403`'d on every `@resourceAuth.app(...)`-gated endpoint, including `GET .../review-history`, `GET .../versions/{id}`, and `GET .../applications/{id}` — endpoints they need to do their job.

- [ ] **Step 1: Add the new error code**

In `src/main/java/com/vnpt/mac/common/exception/ErrorCode.java`, add:
```java
    REVIEW_FEEDBACK_REQUIRED(HttpStatus.BAD_REQUEST),
```

- [ ] **Step 2: Fix `ResourceAuthorizationService.app()` to grant Reviewer access**

In `src/main/java/com/vnpt/mac/security/ResourceAuthorizationService.java`, change:
```java
    public boolean app(UUID appId) {
        var p = current.require();
        return hasRole(p, "PLATFORM_ADMIN") || hasRole(p, "ADMIN")
                || (hasRole(p, "PARTNER_ADMIN") && p.partnerId() != null && ownership.belongsToPartner(appId, p.partnerId()))
                || assignments.findByAppIdAndUserIdAndRevokedAtIsNull(appId, p.userId()).isPresent();
    }
```
to:
```java
    public boolean app(UUID appId) {
        var p = current.require();
        return hasRole(p, "PLATFORM_ADMIN") || hasRole(p, "ADMIN") || hasRole(p, "REVIEWER")
                || (hasRole(p, "PARTNER_ADMIN") && p.partnerId() != null && ownership.belongsToPartner(appId, p.partnerId()))
                || assignments.findByAppIdAndUserIdAndRevokedAtIsNull(appId, p.userId()).isPresent();
    }
```

- [ ] **Step 3: Write the two new enums**

```java
package com.vnpt.mac.applications.entity;

public enum SubmissionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED
}
```

```java
package com.vnpt.mac.applications.entity;

public enum ReviewDecisionType {
    APPROVE,
    REJECT,
    REQUEST_CHANGES
}
```

- [ ] **Step 4: Write `ReviewSubmissionEntity.java` and `ReviewDecisionEntity.java`**

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_submissions")
public class ReviewSubmissionEntity {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "review_round", nullable = false)
    private int reviewRound;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected ReviewSubmissionEntity() {}

    public static ReviewSubmissionEntity create(UUID versionId, UUID partnerId, int reviewRound, UUID submittedBy) {
        var entity = new ReviewSubmissionEntity();
        entity.id = UUID.randomUUID();
        entity.versionId = versionId;
        entity.partnerId = partnerId;
        entity.reviewRound = reviewRound;
        entity.status = SubmissionStatus.PENDING;
        entity.submittedBy = submittedBy;
        entity.submittedAt = Instant.now();
        return entity;
    }

    public void markDecided(SubmissionStatus status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public UUID getPartnerId() { return partnerId; }
    public int getReviewRound() { return reviewRound; }
    public SubmissionStatus getStatus() { return status; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
}
```

```java
package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_decisions")
public class ReviewDecisionEntity {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecisionType decision;

    @Column
    private String feedback;

    @Column(name = "decided_by", nullable = false)
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReviewDecisionEntity() {}

    public static ReviewDecisionEntity create(UUID submissionId, ReviewDecisionType decision, String feedback, UUID decidedBy) {
        var entity = new ReviewDecisionEntity();
        entity.id = UUID.randomUUID();
        entity.submissionId = submissionId;
        entity.decision = decision;
        entity.feedback = feedback;
        entity.decidedBy = decidedBy;
        entity.decidedAt = Instant.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public ReviewDecisionType getDecision() { return decision; }
    public String getFeedback() { return feedback; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
```

- [ ] **Step 5: Write the two repositories**

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ReviewSubmissionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewSubmissionRepository extends JpaRepository<ReviewSubmissionEntity, UUID> {
    Optional<ReviewSubmissionEntity> findTopByVersionIdOrderBySubmittedAtDesc(UUID versionId);
    List<ReviewSubmissionEntity> findByVersionIdOrderBySubmittedAtAsc(UUID versionId);
}
```

```java
package com.vnpt.mac.applications.repository;
import com.vnpt.mac.applications.entity.ReviewDecisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewDecisionRepository extends JpaRepository<ReviewDecisionEntity, UUID> {
    Optional<ReviewDecisionEntity> findBySubmissionId(UUID submissionId);
}
```

- [ ] **Step 6: Write `ReviewDtos.java`**

```java
package com.vnpt.mac.applications.dto;

import com.vnpt.mac.applications.entity.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record ReviewDecisionRequest(@NotNull ReviewDecisionType decision, @Size(max = 1000) String feedback) {
    }

    public record ReviewDecisionResponse(UUID id, ReviewDecisionType decision, String feedback, UUID decidedBy, Instant decidedAt) {
        public static ReviewDecisionResponse from(ReviewDecisionEntity e) {
            return new ReviewDecisionResponse(e.getId(), e.getDecision(), e.getFeedback(), e.getDecidedBy(), e.getDecidedAt());
        }
    }

    public record ReviewSubmissionResponse(UUID id, int reviewRound, SubmissionStatus status, UUID submittedBy,
                                           Instant submittedAt, ReviewDecisionResponse decision) {
        public static ReviewSubmissionResponse from(ReviewSubmissionEntity e, ReviewDecisionResponse decision) {
            return new ReviewSubmissionResponse(e.getId(), e.getReviewRound(), e.getStatus(), e.getSubmittedBy(), e.getSubmittedAt(), decision);
        }
    }
}
```

- [ ] **Step 7: Write `ReviewService.java`**

```java
package com.vnpt.mac.applications.service;

import com.vnpt.mac.applications.dto.ReviewDtos.*;
import com.vnpt.mac.applications.entity.*;
import com.vnpt.mac.applications.repository.*;
import com.vnpt.mac.audit.AuditService;
import com.vnpt.mac.common.exception.BusinessException;
import com.vnpt.mac.common.exception.ErrorCode;
import com.vnpt.mac.security.CurrentUser;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final VersionService versionService;
    private final ApplicationRepository applications;
    private final AppVersionRepository versions;
    private final ValidationRunRepository validationRuns;
    private final ReviewSubmissionRepository submissions;
    private final ReviewDecisionRepository decisions;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ReviewService(VersionService versionService, ApplicationRepository applications, AppVersionRepository versions,
                         ValidationRunRepository validationRuns, ReviewSubmissionRepository submissions,
                         ReviewDecisionRepository decisions, CurrentUser currentUser, AuditService audit) {
        this.versionService = versionService;
        this.applications = applications;
        this.versions = versions;
        this.validationRuns = validationRuns;
        this.submissions = submissions;
        this.decisions = decisions;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ReviewSubmissionResponse submit(UUID appId, UUID versionId) {
        var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        var version = versionService.requireVersion(appId, versionId);
        if (app.getAppType() != ApplicationType.APP2APP) {
            var latestRun = validationRuns.findTopByVersionIdOrderByStartedAtDesc(versionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARTIFACT_MISSING, "Chưa upload artifact/cấu hình cho version này"));
            if (!latestRun.passed())
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Còn lỗi validation mức ERROR, không thể submit");
        }
        version.submit();
        var submission = submissions.save(ReviewSubmissionEntity.create(versionId, version.getPartnerId(), version.getReviewRound(), currentUser.id()));
        var response = ReviewSubmissionResponse.from(submission, null);
        audit.log(version.getPartnerId(), "VERSION_SUBMITTED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional
    public ReviewSubmissionResponse decide(UUID appId, UUID versionId, ReviewDecisionRequest r) {
        var version = versionService.requireVersion(appId, versionId);
        if (version.getStatus() != VersionStatus.IN_REVIEW)
            throw new BusinessException(ErrorCode.VERSION_STATUS_INVALID, "Version không ở trạng thái IN_REVIEW");
        if (r.decision() != ReviewDecisionType.APPROVE && (r.feedback() == null || r.feedback().isBlank()))
            throw new BusinessException(ErrorCode.REVIEW_FEEDBACK_REQUIRED);
        var submission = submissions.findTopByVersionIdOrderBySubmittedAtDesc(versionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_STATUS_INVALID, "Không tìm thấy lượt submit"));
        boolean hadApprovedBefore = versions.existsByAppIdAndStatus(version.getAppId(), VersionStatus.APPROVED);
        var decisionEntity = decisions.save(ReviewDecisionEntity.create(submission.getId(), r.decision(), r.feedback(), currentUser.id()));
        switch (r.decision()) {
            case APPROVE -> {
                version.approve();
                submission.markDecided(SubmissionStatus.APPROVED);
                if (!hadApprovedBefore) {
                    var app = applications.findById(appId).orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
                    app.activate();
                    applications.save(app);
                }
            }
            case REJECT -> {
                version.reject();
                submission.markDecided(SubmissionStatus.REJECTED);
            }
            case REQUEST_CHANGES -> {
                version.requestChanges();
                submission.markDecided(SubmissionStatus.CHANGES_REQUESTED);
            }
        }
        var response = ReviewSubmissionResponse.from(submission, ReviewDecisionResponse.from(decisionEntity));
        audit.log(version.getPartnerId(), "VERSION_REVIEW_DECIDED", "APP_VERSION", versionId, null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReviewSubmissionResponse> history(UUID appId, UUID versionId) {
        versionService.requireVersion(appId, versionId);
        return submissions.findByVersionIdOrderBySubmittedAtAsc(versionId).stream()
                .map(s -> ReviewSubmissionResponse.from(s, decisions.findBySubmissionId(s.getId()).map(ReviewDecisionResponse::from).orElse(null)))
                .toList();
    }
}
```

- [ ] **Step 8: Write `ReviewController.java`**

```java
package com.vnpt.mac.applications.controller;

import com.vnpt.mac.applications.dto.ReviewDtos.*;
import com.vnpt.mac.applications.service.ReviewService;
import com.vnpt.mac.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications/{appId}/versions/{versionId}")
@Tag(name = "Review", description = "Submit, quyết định review, lịch sử review")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('version.submit') and @resourceAuth.app(#appId)")
    @Operation(summary = "Submit version để review", description = "Yêu cầu validation gần nhất PASSED (trừ App2App). Cần quyền version.submit.")
    public ApiResponse<ReviewSubmissionResponse> submit(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.submit(appId, versionId));
    }

    @PostMapping("/review-decisions")
    @PreAuthorize("hasAuthority('version.review')")
    @Operation(summary = "Approve/Reject/Request changes", description = "feedback bắt buộc khi REJECT hoặc REQUEST_CHANGES. Cần quyền version.review (không scope theo partner vì Reviewer làm việc xuyên partner).")
    public ApiResponse<ReviewSubmissionResponse> decide(@PathVariable UUID appId, @PathVariable UUID versionId, @Valid @RequestBody ReviewDecisionRequest r) {
        return ApiResponse.success(service.decide(appId, versionId, r));
    }

    @GetMapping("/review-history")
    @PreAuthorize("hasAuthority('version.read') and @resourceAuth.app(#appId)")
    @Operation(summary = "Lịch sử review", description = "Toàn bộ các lượt submit và quyết định, theo thứ tự thời gian.")
    public ApiResponse<List<ReviewSubmissionResponse>> history(@PathVariable UUID appId, @PathVariable UUID versionId) {
        return ApiResponse.success(service.history(appId, versionId));
    }
}
```

- [ ] **Step 9: Verify it compiles**

Run: `mvn -q compile`
Expected: exit code 0, no output.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/vnpt/mac/applications/entity/SubmissionStatus.java \
        src/main/java/com/vnpt/mac/applications/entity/ReviewDecisionType.java \
        src/main/java/com/vnpt/mac/applications/entity/ReviewSubmissionEntity.java \
        src/main/java/com/vnpt/mac/applications/entity/ReviewDecisionEntity.java \
        src/main/java/com/vnpt/mac/applications/repository/ReviewSubmissionRepository.java \
        src/main/java/com/vnpt/mac/applications/repository/ReviewDecisionRepository.java \
        src/main/java/com/vnpt/mac/common/exception/ErrorCode.java \
        src/main/java/com/vnpt/mac/applications/dto/ReviewDtos.java \
        src/main/java/com/vnpt/mac/applications/service/ReviewService.java \
        src/main/java/com/vnpt/mac/applications/controller/ReviewController.java \
        src/main/java/com/vnpt/mac/security/ResourceAuthorizationService.java
git commit -m "feat(applications): submit/review-decision workflow and review history; fix Reviewer resource scoping"
```

---

### Task 10: Document the new endpoints in `docs/api.md`

**Files:**
- Modify: `docs/api.md`

**Interfaces:**
- None (documentation only).

- [ ] **Step 1: Append a new "5. Application & Version (M2)" section**

Insert a new `## 5. Application & Version — /api/v1/applications` section into `docs/api.md`, right before the existing `## Nguồn tham chiếu` section at the end of the file, following the same format as the Partner section (base path note, per-endpoint permission, request/response JSON). Content:

```markdown
## 5. Application & Version — `/api/v1/applications`

Yêu cầu xác thực + permission tương ứng, cộng thêm `@resourceAuth.app(#appId)` (Partner Admin/Dev chỉ thao tác app thuộc partner/assignment của mình; Platform Admin và Reviewer không bị giới hạn theo partner).

### `GET /api/v1/applications`

Danh sách application. **Quyền**: `app.read.all` (toàn hệ thống) hoặc `app.read` (tự động lọc theo partner của caller).

**Query params**: `status` (`ApplicationStatus`, optional), `appType` (`ApplicationType`, optional), `page`, `size`.

**Response 200**: `PageResponse<ApplicationResponse>`.

### `POST /api/v1/applications`

Tạo Application (wizard) — tạo App shell và version 1 (`DRAFT`) trong 1 transaction. **Quyền**: `app.create`.

**Request body** (`CreateApplicationRequest`)
```json
{
  "appType": "MINIAPP",
  "version": {
    "versionName": "1.0.0",
    "displayName": "My MiniApp",
    "packageName": "com.vnpt.miniapp",
    "descriptionShort": "string",
    "descriptionLong": "string",
    "supportedLanguages": ["vi", "en"],
    "categoryCodes": ["UTILITIES"]
  }
}
```

**Response 200** (`ApplicationResponse`)
```json
{
  "id": "uuid", "appCode": "APP-XXXXXXXX", "appType": "MINIAPP", "status": "DRAFT",
  "firstParty": false, "killSwitchActive": false, "partnerId": "uuid",
  "versionCount": 1, "latestVersion": { "...": "VersionResponse" }, "revision": 0
}
```

### `GET /api/v1/applications/{id}`

Chi tiết application. **Quyền**: `app.read` + resource-owner.

### Version — `/api/v1/applications/{appId}/versions`

- `GET` — danh sách version, lọc theo `status`. **Quyền**: `version.read`.
- `POST` — tạo version mới (`version_code` tự tăng), body là `VersionMetadataFields` (như `version` ở trên). **Quyền**: `version.create`.
- `GET /{versionId}` — chi tiết version. **Quyền**: `version.read`.
- `PATCH /{versionId}` — cập nhật `displayName/descriptionShort/descriptionLong/supportedLanguages/categoryCodes`; chỉ khi version ở `DRAFT`/`CHANGES_REQUESTED`. **Quyền**: `version.update`.

### Artifact & Validation — `/api/v1/applications/{appId}/versions/{versionId}`

- `POST /artifact` (multipart, field `file`) — upload ZIP (MiniApp) hoặc APK/AAB (Feature App). **Quyền**: `artifact.upload`.
- `PUT /webapp-config` — `{ "destinationUrl": "https://..." }`, chỉ cho WebApp. **Quyền**: `artifact.upload`.
- `PUT /module-config` — `{ "moduleNamespace": "string", "description": "string" }`, chỉ cho App Module. **Quyền**: `artifact.upload`.
- `GET /validation` — kết quả validate mới nhất (`ValidationRunResponse`, `findings[]` gồm `ruleCode/severity/message`). **Quyền**: `version.read`.

### Review — `/api/v1/applications/{appId}/versions/{versionId}`

- `POST /submit` — submit version để review; yêu cầu lần validate gần nhất `PASSED` (trừ App2App). **Quyền**: `version.submit`.
- `POST /review-decisions` — `{ "decision": "APPROVE|REJECT|REQUEST_CHANGES", "feedback": "string" }` (`feedback` bắt buộc khi không phải `APPROVE`). **Quyền**: `version.review` (Platform Admin hoặc Reviewer, không giới hạn theo partner).
- `GET /review-history` — toàn bộ các lượt submit + quyết định, theo thứ tự thời gian. **Quyền**: `version.read`.

**Lỗi mới**: `APPLICATION_NOT_FOUND` (404), `VERSION_NOT_FOUND` (404), `VERSION_STATUS_INVALID` (409), `VERSION_NOT_EDITABLE` (409), `CATEGORY_NOT_FOUND` (404), `ARTIFACT_TYPE_MISMATCH` (400), `ARTIFACT_MISSING` (400), `VALIDATION_FAILED` (409), `REVIEW_FEEDBACK_REQUIRED` (400).
```

Also add these new codes to the existing error-code table near the top of the file (same row format as the other entries).

- [ ] **Step 2: Update the "Nguồn tham chiếu" source list**

Add to the bullet list at the bottom of `docs/api.md`:
```
- Controllers: `applications/controller/ApplicationController.java`, `applications/controller/VersionController.java`, `applications/controller/ArtifactController.java`, `applications/controller/ReviewController.java`
- DTOs: `applications/dto/ApplicationDtos.java`, `applications/dto/VersionDtos.java`, `applications/dto/ArtifactDtos.java`, `applications/dto/ReviewDtos.java`
```

- [ ] **Step 3: Commit**

```bash
git add docs/api.md
git commit -m "docs: document M2 application/version API endpoints"
```

---

### Task 11: Full regression + end-to-end smoke test

**Files:** none (verification only).

- [ ] **Step 1: Run the full automated test suite**

Run: `mvn test`
Expected: `BUILD SUCCESS`, all tests pass — including `PartnerEntityTest`, `TotpServiceTest`, `AppVersionEntityTest`, `ArtifactStorageServiceTest`, `ManifestValidationServiceTest`.

- [ ] **Step 2: Start the app against real Postgres**

```bash
docker compose up -d postgres
export BOOTSTRAP_ADMIN_EMAIL=admin@example.com
export BOOTSTRAP_ADMIN_PASSWORD='ChangeMe-123456'
mvn -q spring-boot:run > /tmp/mac-m2-smoke.log 2>&1 &
APP_PID=$!
for i in $(seq 1 30); do curl -sf http://localhost:8080/actuator/health && break; sleep 2; done
```
Expected: health endpoint returns `{"status":"UP"}`.

- [ ] **Step 3: Walk the full M2 vertical slice via curl**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"admin@example.com\",\"password\":\"ChangeMe-123456\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["accessToken"])')

PARTNER_ID=$(curl -s -X POST http://localhost:8080/api/v1/partners \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Partner","contactEmail":"partner@example.com","activateImmediately":true}' \
  | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["id"])')

echo "Partner: $PARTNER_ID"
```
This proves login and partner creation still work end-to-end after the refactor in Task 2. For the application/version flow itself (which requires a partner-scoped principal, not the platform admin), it's enough at this stage to confirm via the admin token that:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"appType":"MINIAPP","version":{"versionName":"1.0.0","displayName":"Smoke MiniApp","packageName":"com.vnpt.smoke","descriptionShort":"smoke test","supportedLanguages":["vi"],"categoryCodes":["UTILITIES"]}}'
```
Expected: `200` with an `ApplicationResponse` whose `latestVersion.status` is `"DRAFT"` and `versionCount` is `1` (the platform admin has no `partnerId`, so this exercises the `app.create` + wizard path independent of partner scoping — this is expected and acceptable for a smoke check).

```bash
APP_ID=$(curl -s http://localhost:8080/api/v1/applications -H "Authorization: Bearer $TOKEN" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["items"][0]["id"])')
VERSION_ID=$(curl -s "http://localhost:8080/api/v1/applications/$APP_ID/versions" -H "Authorization: Bearer $TOKEN" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["items"][0]["id"])')

printf 'zip file content' > /tmp/fake.zip
mkdir -p /tmp/miniapp-fixture && cd /tmp/miniapp-fixture
echo '{"name":"demo"}' > manifest.json
echo '<html></html>' > index.html
zip -q /tmp/miniapp.zip manifest.json index.html
cd -

curl -s -X POST "http://localhost:8080/api/v1/applications/$APP_ID/versions/$VERSION_ID/artifact" \
  -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/miniapp.zip;type=application/zip"

curl -s "http://localhost:8080/api/v1/applications/$APP_ID/versions/$VERSION_ID/validation" -H "Authorization: Bearer $TOKEN"

curl -s -X POST "http://localhost:8080/api/v1/applications/$APP_ID/versions/$VERSION_ID/submit" -H "Authorization: Bearer $TOKEN"

curl -s -X POST "http://localhost:8080/api/v1/applications/$APP_ID/versions/$VERSION_ID/review-decisions" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"decision":"APPROVE"}'

curl -s "http://localhost:8080/api/v1/applications/$APP_ID/versions/$VERSION_ID/review-history" -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8080/api/v1/applications/$APP_ID" -H "Authorization: Bearer $TOKEN"
```

Expected, in order: artifact upload returns an `ArtifactResponse` with a 64-char `checksumSha256`; `/validation` shows `status: "PASSED"` with an `INFO` finding; `/submit` returns a `ReviewSubmissionResponse` with `status: "PENDING"`; `/review-decisions` returns `status: "APPROVED"` with a nested `decision`; `/review-history` shows one entry with round 1 and the `APPROVE` decision; the final `GET /applications/{id}` shows `"status": "ACTIVE"` (flipped by the first approval) and `latestVersion.status: "APPROVED"`.

- [ ] **Step 4: Stop the app**

```bash
kill $APP_PID
```

- [ ] **Step 5: No commit for this task** — it is verification-only. If any step above fails, fix the root cause in the relevant earlier task's files, re-run `mvn test`, and re-commit there (do not paper over failures with try/catch or skipped checks).

---

## Self-Review Notes

- **Spec coverage:** all 9 requested screens map to a task — list/create/detail → Task 4; version list/detail/create/update → Task 5; artifact upload + manifest validation → Tasks 6–8; review history (submit/decide/history) → Task 9. DB schema → Task 1. Docs → Task 10. End-to-end proof → Task 11.
- **Placeholder scan:** no TBD/TODO; every step has real, complete code.
- **Type consistency:** `VersionMetadataFields`, `VersionResponse`, `requireVersion`, `createInitialVersion`, `StoredArtifact`, `ValidationOutcome`/`Finding` are used with the exact same signatures everywhere they're referenced across tasks 4–9.
- **Caught during planning, fixed in Task 9:** `ResourceAuthorizationService.app()` had no path for `REVIEWER`, which would have silently 403'd reviewers on every scoped read endpoint — this is called out explicitly rather than left as a latent bug.
