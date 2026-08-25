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

## Nguồn tham chiếu (source-of-truth)

Tài liệu này được sinh trực tiếp từ source code, đối chiếu lại khi API thay đổi:

- Controllers: `security/AuthController.java`, `partner/controller/PartnerController.java`, `partner/controller/PartnerUserController.java`, `partner/controller/AdminUserController.java`, `partner/controller/ProfileController.java`, `partner/controller/AppAssignmentController.java`, `audit/AuditController.java`
- DTOs: `security/AuthDtos.java`, `partner/dto/PartnerDtos.java`, `partner/dto/UserDtos.java`, `audit/AuditQueryService.java`
- Envelope & lỗi: `common/response/ApiResponse.java`, `common/response/PageResponse.java`, `common/exception/ErrorCode.java`, `common/exception/GlobalExceptionHandler.java`
- Xác thực: `security/SecurityConfig.java`, `security/BearerAuthenticationFilter.java`
