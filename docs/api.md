# MAC M1 Partner API

Tài liệu API kiểu OpenAPI cho 4 nhóm endpoint: **Auth**, **Partner** (bao gồm Partner Users), **User quản trị** (Admin Users / Profile), **App Assignment & Audit**.

Base URL: `http://localhost:8080` (theo README). Toàn bộ endpoint đều dưới prefix `/api/v1`.

## Quy ước chung

### Response envelope

Mọi response thành công đều bọc trong `ApiResponse<T>`:

```json
{
  "data": { /* payload cụ thể của từng endpoint, hoặc null nếu 204 No Content */ },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2026-08-22T10:00:00Z"
  }
}
```

Danh sách phân trang dùng `PageResponse<T>` làm `data`:

```json
{
  "items": [ /* T */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### Response lỗi

```json
{
  "error": {
    "code": "PARTNER_NOT_FOUND",
    "message": "…",
    "fieldErrors": [
      { "field": "email", "message": "must not be blank" }
    ]
  },
  "meta": {
    "requestId": "uuid (lấy từ header X-Request-Id nếu có)",
    "timestamp": "2026-08-22T10:00:00Z"
  }
}
```

| Mã lỗi | HTTP | Ý nghĩa |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Body không hợp lệ (bean validation) |
| `AUTH_INVALID_CREDENTIALS` | 401 | Sai email/password |
| `AUTH_MFA_REQUIRED` | 401 | Cần xác thực 2FA (bước tiếp theo: `POST /auth/mfa/verify`) |
| `AUTH_INVALID_MFA` | 401 | Mã OTP sai/hết hạn |
| `AUTH_FORBIDDEN` | 403 | Không đủ quyền (`AccessDeniedException`) |
| `PARTNER_NOT_FOUND` | 404 | Partner không tồn tại |
| `PARTNER_NOT_ACTIVE` | 409 | Partner chưa active |
| `PARTNER_TAX_CODE_EXISTS` | 409 | Mã số thuế trùng |
| `PARTNER_STATUS_INVALID` | 409 | Chuyển trạng thái partner không hợp lệ |
| `PARTNER_QUOTA_EXCEEDED` | 409 | Vượt quota |
| `USER_NOT_FOUND` | 404 | User không tồn tại |
| `USER_EMAIL_EXISTS` | 409 | Email đã tồn tại |
| `USER_STATUS_INVALID` | 409 | Trạng thái user không hợp lệ cho thao tác |
| `ROLE_NOT_FOUND` | 404 | Role không tồn tại |
| `INVITATION_INVALID` | 400 | Token mời không hợp lệ/hết hạn |
| `API_TOKEN_NOT_FOUND` | 404 | Personal API token không tồn tại |
| `ASSIGNMENT_INVALID` | 400 | Gán developer không hợp lệ |
| `CONCURRENT_MODIFICATION` | 409 | Ghi đè do optimistic locking (`revision` lệch) |
| `APPLICATION_NOT_FOUND` | 404 | Application không tồn tại |
| `VERSION_NOT_FOUND` | 404 | Version không tồn tại |
| `VERSION_STATUS_INVALID` | 409 | Chuyển trạng thái version không hợp lệ |
| `VERSION_NOT_EDITABLE` | 409 | Version không ở trạng thái cho phép chỉnh sửa |
| `CATEGORY_NOT_FOUND` | 404 | Category không tồn tại |
| `ARTIFACT_TYPE_MISMATCH` | 400 | Loại artifact không khớp với app type |
| `ARTIFACT_MISSING` | 400 | Artifact chưa được upload |
| `VALIDATION_FAILED` | 409 | Validation không thành công |
| `REVIEW_FEEDBACK_REQUIRED` | 400 | Feedback là bắt buộc cho quyết định này |

### Xác thực

Header `Authorization: Bearer <token>`, hai loại token được chấp nhận:

- **JWT access token** — lấy từ `POST /api/v1/auth/login` (hoặc `/mfa/verify`), gắn với toàn bộ quyền/role của user.
- **Personal API token** — chuỗi có prefix `mac_pat_<prefix>_<secret>`, tạo qua `POST /api/v1/me/api-tokens`; quyền bị giới hạn theo `scopes` khai báo lúc tạo token.

Chỉ `/api/v1/auth/**` và `/actuator/health` là public; mọi endpoint khác yêu cầu đã xác thực, cộng thêm `@PreAuthorize` theo permission/role cụ thể (ghi ở từng endpoint bên dưới). User phải ở trạng thái `ACTIVE` thì token mới được chấp nhận.

### Enum dùng chung

- `PartnerStatus`: `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `SUSPENDED`, `TERMINATED`
- `UserStatus`: `INVITED`, `ACTIVE`, `LOCKED`, `DISABLED`
- `RoleCode`: `PLATFORM_ADMIN`, `ADMIN`, `PARTNER_ADMIN`, `PARTNER_DEVELOPER`, `REVIEWER`

---

## 1. Auth — `/api/v1/auth`

Không yêu cầu xác thực (`permitAll`).

### `POST /api/v1/auth/login`

Đăng nhập bằng email/password. Nếu tài khoản bật 2FA, trả về `challengeToken` thay vì `accessToken`.

**Request body** (`LoginRequest`)
```json
{
  "email": "user@example.com",   // required, email hợp lệ
  "password": "string"           // required
}
```

**Response 200** (`LoginResponse`)
```json
{
  "mfaRequired": false,
  "accessToken": "jwt-string | null",
  "challengeToken": "string | null",   // chỉ có khi mfaRequired = true
  "expiresInSeconds": 3600
}
```

**Lỗi**: `AUTH_INVALID_CREDENTIALS` (401), `VALIDATION_ERROR` (400).

---

### `POST /api/v1/auth/mfa/verify`

Xác nhận mã OTP 6 số để hoàn tất đăng nhập sau khi nhận `challengeToken`.

**Request body** (`MfaVerifyRequest`)
```json
{
  "challengeToken": "string",   // required
  "code": "123456"              // required, đúng 6 chữ số
}
```

**Response 200** (`LoginResponse`) — giống `login`, lần này có `accessToken`.

**Lỗi**: `AUTH_INVALID_MFA` (401), `VALIDATION_ERROR` (400).

---

### `POST /api/v1/auth/invitations/accept`

Chấp nhận lời mời tham gia (partner user hoặc admin), đặt mật khẩu lần đầu.

**Request body** (`AcceptInvitationRequest`)
```json
{
  "token": "string",       // required — invitation token
  "password": "string"     // required, 12-128 ký tự
}
```

**Response**: `204 No Content`.

**Lỗi**: `INVITATION_INVALID` (400), `VALIDATION_ERROR` (400).

---

## 2. Partner — `/api/v1/partners`

Yêu cầu xác thực + permission tương ứng. Nhiều endpoint còn kiểm tra `@resourceAuth.partner(#id)` (user chỉ thao tác trên partner của chính mình, trừ khi có quyền admin toàn cục).

### `GET /api/v1/partners`

Danh sách partner (có filter + phân trang). **Quyền**: `partner.read.all`.

**Query params**: `status` (`PartnerStatus`, optional), `q` (optional, tìm theo tên), `page` (default 0), `size` (default 20, tối đa 100).

**Response 200**: `PageResponse<PartnerResponse>`.

### `POST /api/v1/partners`

Tạo partner mới. **Quyền**: `partner.create`.

**Request body** (`CreatePartnerRequest`)
```json
{
  "name": "string",              // required, max 255
  "taxCode": "string",           // optional, max 50
  "contactEmail": "user@x.com",  // required, email
  "contactPhone": "string",      // optional, max 30
  "activateImmediately": false
}
```

**Response 201/200** (`PartnerResponse`) — xem schema bên dưới.

**Lỗi**: `PARTNER_TAX_CODE_EXISTS` (409), `VALIDATION_ERROR` (400).

### `GET /api/v1/partners/{id}`

Chi tiết partner. **Quyền**: `partner.read` + resource-owner.

**Response 200** (`PartnerResponse`)
```json
{
  "id": "uuid",
  "partnerCode": "string",
  "name": "string",
  "taxCode": "string | null",
  "contactEmail": "string",
  "contactPhone": "string | null",
  "status": "PENDING_APPROVAL",
  "suspendReason": "string | null",
  "suspendedAt": "instant | null",
  "revision": 0
}
```

**Lỗi**: `PARTNER_NOT_FOUND` (404).

### `GET /api/v1/partners/{id}/status-history`

Lịch sử đổi trạng thái. **Quyền**: `partner.read` + resource-owner.

**Response 200**: `StatusHistoryResponse[]`
```json
[{
  "id": "uuid",
  "fromStatus": "PENDING_APPROVAL",
  "toStatus": "ACTIVE",
  "reason": "string | null",
  "changedBy": "uuid",
  "changedAt": "instant"
}]
```

### `PATCH /api/v1/partners/{id}`

Cập nhật thông tin partner. **Quyền**: `partner.update` + resource-owner.

**Request body** (`UpdatePartnerRequest`)
```json
{
  "name": "string",              // required, max 255
  "contactEmail": "user@x.com",  // required
  "contactPhone": "string"       // optional, max 30
}
```

**Response 200**: `PartnerResponse`. **Lỗi**: `PARTNER_NOT_FOUND`, `CONCURRENT_MODIFICATION` (409, dựa trên `revision`).

### `POST /api/v1/partners/{id}/approve`

Duyệt partner (`PENDING_APPROVAL` → `ACTIVE`). **Quyền**: `partner.approve`. Không có body.

**Response 200**: `PartnerResponse`. **Lỗi**: `PARTNER_STATUS_INVALID` (409).

### `POST /api/v1/partners/{id}/reject`

Từ chối partner. **Quyền**: `partner.approve`.

**Request body** (`ReasonRequest`)
```json
{ "reason": "string" }   // required, 5-1000 ký tự
```

**Response 200**: `PartnerResponse`.

### `POST /api/v1/partners/{id}/suspend`

Tạm ngưng partner (`ACTIVE` → `SUSPENDED`). **Quyền**: `partner.suspend`.

**Request body**: `ReasonRequest` (như trên). **Response 200**: `PartnerResponse`.

### `POST /api/v1/partners/{id}/unsuspend`

Bỏ tạm ngưng. **Quyền**: `partner.suspend`. Không có body.

**Response 200**: `PartnerResponse`.

### `GET /api/v1/partners/{id}/quota`

Xem quota hiện tại. **Quyền**: `quota.read` + resource-owner.

**Response 200** (`QuotaResponse`)
```json
{
  "partnerId": "uuid",
  "maxApps": 10,
  "maxDevelopers": 5,
  "maxConcurrentSubmissions": 2,
  "maxStorageBytes": 1073741824,
  "developerUsage": 3,
  "appUsage": 4
}
```

### `PUT /api/v1/partners/{id}/quota`

Cập nhật quota. **Quyền**: `quota.update` (không yêu cầu resource-owner — chỉ platform/admin cấu hình).

**Request body** (`UpdateQuotaRequest`)
```json
{
  "maxApps": 10,                    // >= 0
  "maxDevelopers": 5,               // >= 0
  "maxConcurrentSubmissions": 2,    // >= 0
  "maxStorageBytes": 1073741824     // >= 0
}
```

**Response 200**: `QuotaResponse`. **Lỗi**: `PARTNER_QUOTA_EXCEEDED` (409) nếu usage hiện tại vượt giới hạn mới.

---

### Partner Users — `/api/v1/partners/{partnerId}/users`

### `GET /api/v1/partners/{partnerId}/users`

Danh sách user thuộc 1 partner. **Quyền**: `user.read` + resource-owner (`partnerId`).

**Query params**: `page` (default 0), `size` (default 20, tối đa 100).

**Response 200**: `PageResponse<UserResponse>` (schema `UserResponse` xem mục 3).

### `POST /api/v1/partners/{partnerId}/users/invitations`

Mời user mới vào partner. **Quyền**: `user.invite` + resource-owner.

**Request body** (`InviteUserRequest`)
```json
{
  "email": "user@x.com",   // required
  "fullName": "string",    // required, max 255
  "role": "PARTNER_ADMIN"  // required, RoleCode
}
```

**Response 200** (`InvitationResponse`)
```json
{
  "userId": "uuid",
  "invitationToken": "string",
  "expiresAt": "instant"
}
```

> Ghi chú (README): invitation token hiện được trả trực tiếp qua API để phục vụ test độc lập; production nên gửi qua notification worker.

**Lỗi**: `USER_EMAIL_EXISTS` (409), `ROLE_NOT_FOUND` (404), `PARTNER_NOT_ACTIVE` (409), `PARTNER_QUOTA_EXCEEDED` (409).

---

## 3. User quản trị — Admin Users & Profile

### Admin Users — `/api/v1/admin-users`

Quản lý tài khoản nội bộ (platform admin / reviewer…), không gắn với partner. **Quyền cho cả 2 endpoint**: `admin.manage`.

#### `GET /api/v1/admin-users`

**Query params**: `page` (default 0), `size` (default 20, tối đa 100).

**Response 200**: `PageResponse<UserResponse>`.

#### `POST /api/v1/admin-users`

**Request body** (`CreateAdminRequest`)
```json
{
  "email": "user@x.com",  // required
  "fullName": "string",   // required
  "role": "PLATFORM_ADMIN" // required, RoleCode
}
```

**Response 200**: `InvitationResponse` (giống mục Partner Users).

---

### Profile (tài khoản đang đăng nhập) — `/api/v1/me`

Tất cả endpoint chỉ cần xác thực (không cần permission đặc biệt), trừ 2 endpoint API token yêu cầu `token.manage.own`.

#### `GET /api/v1/me`

Thông tin user hiện tại.

**Response 200** (`UserResponse`)
```json
{
  "id": "uuid",
  "partnerId": "uuid | null",
  "email": "string",
  "fullName": "string",
  "publicEmail": "string | null",
  "bio": "string | null",
  "status": "ACTIVE",
  "mfaEnabled": false,
  "roles": ["PARTNER_ADMIN"],
  "revision": 0
}
```

#### `PATCH /api/v1/me`

Cập nhật hồ sơ.

**Request body** (`UpdateProfileRequest`)
```json
{
  "fullName": "string",     // required, max 255
  "publicEmail": "string",  // optional, email
  "bio": "string"           // optional, max 1000
}
```

**Response 200**: `UserResponse`.

#### `POST /api/v1/me/change-password`

**Request body** (`ChangePasswordRequest`)
```json
{
  "currentPassword": "string",  // required
  "newPassword": "string"       // required, 12-128 ký tự
}
```

**Response**: `204 No Content`. **Lỗi**: `AUTH_INVALID_CREDENTIALS` (401) nếu `currentPassword` sai.

#### `POST /api/v1/me/mfa/setup`

Khởi tạo bật 2FA — sinh secret TOTP mới (chưa kích hoạt).

**Response 200** (`MfaSetupResponse`)
```json
{
  "secret": "base32-string",
  "otpauthUri": "otpauth://totp/..."
}
```

#### `POST /api/v1/me/mfa/confirm`

Xác nhận và bật 2FA bằng mã OTP đầu tiên.

**Request body** (`MfaCodeRequest`)
```json
{ "code": "123456" }   // required, 6 chữ số
```

**Response**: `204 No Content`. **Lỗi**: `AUTH_INVALID_MFA` (401).

#### `DELETE /api/v1/me/mfa`

Tắt 2FA (yêu cầu mã OTP hiện tại để xác nhận).

**Request body**: `MfaCodeRequest` (như trên).

**Response**: `204 No Content`.

#### `GET /api/v1/me/api-tokens`

Danh sách personal API token của chính mình. **Quyền**: `token.manage.own`.

**Response 200**: `ApiTokenResponse[]`
```json
[{
  "id": "uuid",
  "name": "string",
  "prefix": "string",
  "scopes": ["partner.read"],
  "expiresAt": "instant | null",
  "lastUsedAt": "instant | null",
  "revokedAt": "instant | null",
  "createdAt": "instant"
}]
```

#### `POST /api/v1/me/api-tokens`

Tạo personal API token mới (dùng cho CI/CD). **Quyền**: `token.manage.own`.

**Request body** (`CreateApiTokenRequest`)
```json
{
  "name": "string",             // required, max 100
  "scopes": ["partner.read"],   // required, không rỗng
  "expiresInDays": 90           // required, 1-365
}
```

**Response 200** (`CreatedApiTokenResponse`)
```json
{
  "id": "uuid",
  "token": "mac_pat_<prefix>_<secret>",  // chỉ trả về DUY NHẤT lần này
  "prefix": "string",
  "scopes": ["partner.read"],
  "expiresAt": "instant"
}
```

#### `POST /api/v1/me/api-tokens/{id}/revoke`

Thu hồi token. **Quyền**: `token.manage.own`.

**Response**: `204 No Content`. **Lỗi**: `API_TOKEN_NOT_FOUND` (404).

---

## 4. App Assignment & Audit

### `PUT /api/v1/apps/{appId}/developer-assignments`

Thay thế toàn bộ danh sách developer được gán cho 1 app. Quyền sở hữu app được xác định qua bảng `applications` (partner_id). **Quyền**: `app.assign` + `@resourceAuth.app(#appId)`.

**Request body** (`AssignDevelopersRequest`)
```json
{ "developerIds": ["uuid", "uuid"] }   // required, không rỗng
```

**Response 200**: `UUID[]` — danh sách developer ID sau khi thay thế.

**Lỗi**: `ASSIGNMENT_INVALID` (400, ví dụ app không tồn tại/không thuộc partner, hoặc developer không thuộc partner sở hữu app), `PARTNER_QUOTA_EXCEEDED` (409, vượt `maxDevelopers`).

---

### Audit Logs — `/api/v1/audit-logs`

Chỉ đọc, dùng để tra cứu nhật ký hành động (before/after state, actor, IP…).

#### `GET /api/v1/audit-logs`

Toàn bộ audit log hệ thống. **Quyền**: role `PLATFORM_ADMIN`.

**Query params**: `page` (default 0), `size` (default 20, tối đa 100).

**Response 200**: `PageResponse<AuditResponse>`.

#### `GET /api/v1/audit-logs/partner/{partnerId}`

Audit log giới hạn theo 1 partner. **Quyền**: `@resourceAuth.partner(#partnerId)` (partner admin xem log của chính mình, hoặc platform admin xem log bất kỳ partner nào).

**Query params**: giống trên.

**`AuditResponse` schema** (dùng chung cho cả 2 endpoint)
```json
{
  "id": "uuid",
  "actorUserId": "uuid | null",
  "actorEmail": "string | null",
  "actorRoles": "string | null",
  "partnerId": "uuid | null",
  "action": "string",
  "resourceType": "string",
  "resourceId": "uuid | null",
  "ipAddress": "string | null",
  "userAgent": "string | null",
  "beforeState": "string (JSON) | null",
  "afterState": "string (JSON) | null",
  "correlationId": "string | null",
  "createdAt": "instant"
}
```

---

## 5. Application & Version — `/api/v1/applications`

Yêu cầu xác thực + permission tương ứng, cộng thêm `@resourceAuth.app(#appId)` (Partner Admin/Dev chỉ thao tác app thuộc partner/assignment của mình; Platform Admin và Reviewer không bị giới hạn theo partner).

### `GET /api/v1/applications`

Danh sách application. **Quyền**: `app.read.all` (toàn hệ thống) hoặc `app.read` (tự động lọc theo partner của caller — `partnerId` truyền vào bị bỏ qua đối với caller không có `app.read.all`/Reviewer).

**Query params**: `status` (`ApplicationStatus`, optional), `appType` (`ApplicationType`, optional), `partnerId` (`UUID`, optional — chỉ có hiệu lực với caller `app.read.all` hoặc Reviewer), `page`, `size`.

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
- `POST /review-decisions` — `{ "decision": "APPROVE|REJECT|REQUEST_CHANGES", "feedback": "string" }` (`feedback` bắt buộc khi không phải `APPROVE`). **Quyền**: `version.review` (Platform Admin hoặc Reviewer, không giới hạn theo partner). Chặn `APPROVE` nếu còn permission ở `PENDING_REVIEW`.
- `GET /review-history` — toàn bộ các lượt submit + quyết định, theo thứ tự thời gian. **Quyền**: `version.read`.

### Review Queue (xuyên application) — `GET /api/v1/versions`

Hàng đợi review xuyên suốt mọi application — dùng cho màn Review Center thay vì phải vào từng application để tìm version đang review. **Quyền**: `version.review` (không scope theo app/partner, giống `review-decisions`).

**Query params**: `status` (`VersionStatus`, optional — vd `IN_REVIEW`), `page`, `size`.

**Response 200**: `PageResponse<VersionResponse>` (mỗi item có `appId` để điều hướng sang trang chi tiết/quyết định của đúng application).

## 6. Permission Catalog (M3) — `/api/v1/applications/{appId}/versions/{versionId}/permissions`

Khai báo & duyệt quyền thiết bị (`CAMERA`, `MICROPHONE`, ...) theo version. Mỗi permission chỉ được yêu cầu 1 lần/version (`code` không trùng). `justification` bắt buộc, tối thiểu 20 ký tự.

**Luồng trạng thái** (`PermissionRequestStatus`): request được phân loại ngay khi tạo — `BLOCKED` nếu vi phạm rule theo `appType` (PC-04, ví dụ WebApp xin `CAMERA`); `AUTO_APPROVED` nếu `sensitivity=NORMAL` và không phải escalation; ngược lại `PENDING_REVIEW`. `PENDING_REVIEW` chỉ chuyển tiếp qua `APPROVED`/`REJECTED` (`decide`, `reason` bắt buộc khi `REJECT`). `is_escalation` so sánh với version `APPROVED` gần nhất của app: quyền mới hoặc tăng mức nhạy cảm (`NORMAL < DANGEROUS < SIGNATURE`) so với version nền → escalation, ép `PENDING_REVIEW` (bỏ qua nhánh auto-approve) — kể cả khi app chưa có version nào `APPROVED` (mọi permission trên version đầu tiên đều tính là mới).

`BLOCKED` chặn `submit` (`PERMISSION_BLOCKED`); `PENDING_REVIEW` chặn `APPROVE` version (`PERMISSION_PENDING_REVIEW`) — xem mục 5, Review.

### `GET /api/v1/permissions/catalog`

Danh mục permission đang active (`code`, `displayName`, `sensitivity`, `requiresManualReview`). **Quyền**: `permission.catalog.read`.

### `GET .../permissions`

Danh sách permission đã khai báo trên version. **Quyền**: `version.read` + resource-owner.

### `POST .../permissions`

Khai báo permission mới từ catalog.

**Request body** (`RequestPermissionRequest`)
```json
{ "permissionCode": "CAMERA", "justification": "Cần camera để quét mã QR thanh toán" }
```

**Response 200** (`AppVersionPermissionResponse`)
```json
{
  "id": "uuid", "versionId": "uuid", "permissionCode": "CAMERA", "displayName": "Camera",
  "justification": "string", "resolvedSensitivity": "DANGEROUS", "status": "PENDING_REVIEW",
  "isEscalation": true, "decidedBy": null, "decisionReason": null,
  "createdAt": "iso-instant", "decidedAt": null
}
```

**Quyền**: `permission.request` + resource-owner. Chỉ khi version ở `DRAFT`/`CHANGES_REQUESTED`.

### `DELETE .../permissions/{permissionRequestId}`

Gỡ permission đã khai báo. **Quyền**: `permission.request` + resource-owner. Chỉ khi version ở `DRAFT`/`CHANGES_REQUESTED`. **Response**: `204 No Content`.

### `POST .../permissions/{permissionRequestId}/decide`

Duyệt/Từ chối permission đang `PENDING_REVIEW`.

**Request body** (`DecidePermissionRequest`)
```json
{ "decision": "APPROVE|REJECT", "reason": "string" }
```
`reason` bắt buộc khi `REJECT`. **Quyền**: `permission.decide` (Reviewer/Platform Admin, không giới hạn theo partner).

### `GET .../permissions/{permissionRequestId}/history`

Lịch sử sự kiện (`REQUESTED`, `DECIDED`) của 1 permission, theo thứ tự thời gian (PC-07). **Quyền**: `version.read` + resource-owner.

```json
[{ "id": "uuid", "eventType": "REQUESTED", "actorId": "uuid", "note": "string", "createdAt": "iso-instant" }]
```

### Quản trị catalog — `/api/v1/permissions/catalog` (Platform Admin)

- `GET .../catalog/all` — toàn bộ catalog kể cả `isActive=false` (dùng cho màn quản trị). **Quyền**: `permission.catalog.manage`.
- `POST .../catalog` — tạo permission catalog entry mới (`code` phải unique). Body (`CreatePermissionCatalogRequest`): `{ "code": "BLUETOOTH", "displayName": "Bluetooth", "sensitivity": "NORMAL", "requiresManualReview": false }`. **Quyền**: `permission.catalog.manage`.
- `PATCH .../catalog/{permissionId}` — cập nhật `displayName/sensitivity/requiresManualReview/isActive` (đặt `isActive=false` để deactivate, không xoá cứng). Body (`UpdatePermissionCatalogRequest`). **Quyền**: `permission.catalog.manage`.
- `PUT .../catalog/{permissionId}/rules/{appType}` — tạo hoặc cập nhật (upsert) rule cho 1 cặp (permission, appType). Body (`UpsertAppTypeRuleRequest`): `{ "effect": "ALLOW|DENY|CONDITIONAL", "reason": "string" }`. **Quyền**: `permission.catalog.manage`.
- `DELETE .../catalog/{permissionId}/rules/{appType}` — gỡ rule, appType đó quay về mặc định `ALLOW`. **Quyền**: `permission.catalog.manage`. **Response**: `204 No Content`.

---

## 7. Capability Catalog (M4) — `/api/v1/applications/{appId}/versions/{versionId}/capabilities`

Khai báo & duyệt capability runtime (`PUSH_NOTIFICATION`, `BACKGROUND_LOCATION`, `DEEP_LINK`, `BIOMETRIC_AUTH`, ...) theo version. Kiến trúc đồng bộ với M3 (Partner Dev khai báo → Reviewer duyệt) nhưng đơn giản hơn: không có mức nhạy cảm (`sensitivity`) hay escalation detection — catalog chỉ giới hạn theo `allowedAppTypes` (CC-01). Mỗi capability chỉ được yêu cầu 1 lần/version. **Không có `justification` bắt buộc** (khác Permission) và **không nằm trong điều kiện `APPROVE` version** — capability request không chặn submit/approve của version (theo §3.3 của design, chỉ `app_version_permissions`, Public Intent, và validation finding mới là điều kiện).

**Luồng trạng thái** (`CapabilityRequestStatus`): `BLOCKED` nếu `appType` của app không nằm trong `allowedAppTypes` của capability; ngược lại `PENDING_REVIEW` (không có nhánh auto-approve). `PENDING_REVIEW` chỉ chuyển tiếp qua `APPROVED`/`REJECTED` (`decide`, `reason` bắt buộc khi `REJECT`).

### `GET /api/v1/capabilities/catalog`

Danh mục capability đang active (`code`, `displayName`, `allowedAppTypes`). **Quyền**: `capability.catalog.read`.

### Quản trị catalog — `/api/v1/capabilities/catalog` (Platform Admin)

- `GET .../catalog/all` — toàn bộ catalog kể cả inactive. **Quyền**: `capability.catalog.manage`.
- `POST .../catalog` — tạo capability catalog entry mới. Body (`CreateCapabilityCatalogRequest`): `{ "code": "PUSH_NOTIFICATION", "displayName": "Gửi thông báo đẩy", "allowedAppTypes": ["MINIAPP","WEBAPP"] }`. **Quyền**: `capability.catalog.manage`.
- `PATCH .../catalog/{capabilityId}` — cập nhật `displayName/allowedAppTypes/isActive`. **Quyền**: `capability.catalog.manage`.

### `GET .../capabilities`

Danh sách capability đã khai báo trên version. **Quyền**: `version.read` + resource-owner.

### `POST .../capabilities`

Khai báo capability mới từ catalog.

**Request body** (`RequestCapabilityRequest`)
```json
{ "capabilityCode": "PUSH_NOTIFICATION" }
```

**Response 200** (`AppVersionCapabilityResponse`)
```json
{
  "id": "uuid", "versionId": "uuid", "capabilityCode": "PUSH_NOTIFICATION", "displayName": "Gửi thông báo đẩy",
  "status": "PENDING_REVIEW", "decidedBy": null, "decisionReason": null,
  "createdAt": "iso-instant", "decidedAt": null
}
```

**Quyền**: `capability.request` + resource-owner. Chỉ khi version ở `DRAFT`/`CHANGES_REQUESTED`.

### `DELETE .../capabilities/{capabilityRequestId}`

Gỡ capability đã khai báo. **Quyền**: `capability.request` + resource-owner. Chỉ khi version ở `DRAFT`/`CHANGES_REQUESTED`. **Response**: `204 No Content`.

### `POST .../capabilities/{capabilityRequestId}/decide`

Duyệt/Từ chối capability đang `PENDING_REVIEW`.

**Request body** (`DecideCapabilityRequest`)
```json
{ "decision": "APPROVE|REJECT", "reason": "string" }
```
`reason` bắt buộc khi `REJECT`. **Quyền**: `capability.decide` (Reviewer/Platform Admin, không giới hạn theo partner).

---

## Nguồn tham chiếu (source-of-truth)

Tài liệu này được sinh trực tiếp từ source code, đối chiếu lại khi API thay đổi:

- Controllers: `security/AuthController.java`, `partner/controller/PartnerController.java`, `partner/controller/PartnerUserController.java`, `partner/controller/AdminUserController.java`, `partner/controller/ProfileController.java`, `partner/controller/AppAssignmentController.java`, `audit/AuditController.java`, `applications/controller/ApplicationController.java`, `applications/controller/VersionController.java`, `applications/controller/ArtifactController.java`, `applications/controller/ReviewController.java`, `applications/controller/PermissionController.java`, `applications/controller/CapabilityController.java`
- DTOs: `security/AuthDtos.java`, `partner/dto/PartnerDtos.java`, `partner/dto/UserDtos.java`, `audit/AuditQueryService.java`, `applications/dto/ApplicationDtos.java`, `applications/dto/VersionDtos.java`, `applications/dto/ArtifactDtos.java`, `applications/dto/ReviewDtos.java`, `applications/dto/PermissionDtos.java`, `applications/dto/CapabilityDtos.java`
- Envelope & lỗi: `common/response/ApiResponse.java`, `common/response/PageResponse.java`, `common/exception/ErrorCode.java`, `common/exception/GlobalExceptionHandler.java`
- Xác thực: `security/SecurityConfig.java`, `security/BearerAuthenticationFilter.java`
