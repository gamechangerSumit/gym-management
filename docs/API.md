# API contract (v1)

Base path `/api/v1`. JSON, camelCase. This file is the contract shared by backend and frontend.

## Conventions

- **Money**: integer paise (`amountPaise`, `pricePaise`, ...). The UI formats as INR.
- **Dates**: `YYYY-MM-DD` (gym timezone, default Asia/Kolkata). **Timestamps**: ISO-8601 UTC (`2026-10-13T09:30:00Z`).
- **Auth**: server-side session cookie `SESSION` (HttpOnly, SameSite=Lax, Secure in prod).
- **CSRF**: cookie `XSRF-TOKEN` (readable by JS). Send its value in header `X-XSRF-TOKEN` on every POST/PUT/PATCH/DELETE.
  The cookie is issued on the first request (call `GET /auth/session` on app start; a 401 still sets it).
- **Unknown JSON fields are rejected** with 400 (`VALIDATION_FAILED`).
- **Errors**: HTTP status plus
  `{ "code": "STRING", "message": "human readable", "fieldErrors": [{"field":"phone","message":"..."}], "requestId": "...", "timestamp": "..." }`
- **Paging**: query `page` (0-based, default 0), `size` (default 20, max 100).
  Response `{ "items": [...], "page": 0, "size": 20, "total": 134 }`.
- Business outcomes of scanning are returned with HTTP 200 and a `result` code (see Attendance).

Common error codes: `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `PASSWORD_CHANGE_REQUIRED` (403), `NOT_FOUND` (404),
`VALIDATION_FAILED` (400), `CONFLICT`/specific (409), `TOO_MANY_ATTEMPTS` (429), `INTERNAL_ERROR` (500).

Password policy: 10-72 characters, at least one letter and one digit (`WEAK_PASSWORD`).

## Auth

| Method | Path | Body | Result |
|---|---|---|---|
| GET | /auth/session | - | 200 SessionInfo, 401 if not signed in |
| POST | /auth/login | `{loginId, password}` | 200 SessionInfo; 401 `INVALID_CREDENTIALS`; 429 `TOO_MANY_ATTEMPTS` |
| POST | /auth/logout | - | 204 |
| POST | /auth/activate | `{token, password}` | 204; 400 `INVALID_TOKEN` / `WEAK_PASSWORD` |
| POST | /auth/change-password | `{currentPassword, newPassword}` | 204; 400 `WRONG_PASSWORD` / `WEAK_PASSWORD` |
| POST | /auth/password-reset/request | `{loginId}` | 202 always (no delivery provider; owner sees the request in the audit log and shares a reset link) |
| POST | /auth/password-reset/confirm | `{token, newPassword}` | 204; 400 `INVALID_TOKEN` / `WEAK_PASSWORD` |

`SessionInfo`:
```json
{ "userId": 1, "loginId": "owner", "displayName": "Owner", "role": "OWNER|STAFF|MEMBER",
  "mustChangePassword": false,
  "permissions": {"canRecordPayments": true, "canManageMemberships": true, "canEditMembers": true, "canViewReports": true},
  "gymName": "My Gym", "demoMode": false }
```
Owner: all permissions true. Member: all false. While `mustChangePassword` is true every endpoint except
`/auth/session`, `/auth/logout`, `/auth/change-password` returns 403 `PASSWORD_CHANGE_REQUIRED`.

## Settings

- `GET /settings` (owner, staff) / `PUT /settings` (owner):
  `{gymName, timezone, duplicateWindowMinutes, dropOffDays, expiringSoonDays, memberCanEditEmail}`

## Staff (owner only)

`StaffDto`: `{userId, loginId, displayName, status: "INVITED|ACTIVE|DISABLED", permissions:{...4 flags}, createdAt}`

- `GET /staff` -> `StaffDto[]`
- `POST /staff` `{loginId, displayName, permissions}` -> 201 `{staff, link, expiresAt}` (activation link, shown once)
- `PUT /staff/{userId}` `{displayName, status: "ACTIVE|DISABLED", permissions}` -> `StaffDto`
- `POST /staff/{userId}/reset-link` -> `{loginId, link, expiresAt}`

Links look like `{publicBaseUrl}/activate?token=...` and `{publicBaseUrl}/reset-password?token=...`.

## Members (owner, staff)

`MemberSummary`: `{id, memberCode, fullName, phone, status:"ACTIVE|INACTIVE", joinDate, membershipStatus: MembershipStatus|null, membershipEndDate|null}`

`MemberDetail`: `{id, memberCode, fullName, phone, email, joinDate, emergencyContact, staffNotes, status, accountStatus:"NONE|INVITED|ACTIVE|DISABLED", currentMembership: MembershipDto|null, createdAt, demo}`

`MemberInput`: `{fullName, phone, email?, joinDate?, emergencyContact?, staffNotes?}`

- `GET /members?q=&status=&page=&size=` -> Page<MemberSummary> (q matches name, phone, member code)
- `POST /members` (needs canEditMembers) -> 201 `{member: MemberDetail, warnings: string[]}`; 409 `DUPLICATE_PHONE`
- `GET /members/{id}` -> MemberDetail
- `PUT /members/{id}` (canEditMembers) -> `{member, warnings}`
- `POST /members/{id}/deactivate`, `/reactivate` (canEditMembers) -> MemberDetail
- `POST /members/{id}/invite` (owner) -> 201 `{loginId, link, expiresAt}`; 409 `ALREADY_ACTIVE`
- `POST /members/{id}/reset-link` (owner) -> `{loginId, link, expiresAt}`; 409 `NO_ACTIVE_ACCOUNT`
- `GET /members/{id}/memberships` -> MembershipDto[]
- `GET /members/{id}/financials` (canRecordPayments) -> `{chargedPaise, paidPaise, duesPaise, memberships: MembershipDto[], payments: PaymentDto[]}`
  (`duesPaise` negative means credit)

## Plans

`PlanDto`: `{id, name, durationDays, pricePaise, description, active}`

- `GET /plans?activeOnly=false` (owner, staff), `POST /plans` (owner) -> 201, `PUT /plans/{id}` (owner)

## Memberships

`MembershipStatus`: `SCHEDULED | ACTIVE | EXPIRING_SOON | EXPIRED | ON_HOLD | CANCELLED`

`MembershipDto`: `{id, memberId, planId, planName, startDate, endDate, priceChargedPaise, storedStatus:"NORMAL|ON_HOLD|CANCELLED", status: MembershipStatus, daysRemaining, renewedFromId|null, createdAt}`
(`daysRemaining = endDate - today`; end date is inclusive; negative once expired)

- `POST /members/{id}/memberships` `{planId, startDate?, priceChargedPaise?}` (canManageMemberships) -> 201; 409 `MEMBERSHIP_OVERLAP`
- `POST /memberships/{id}/renew` `{planId?, startDate?, priceChargedPaise?}` -> 201 (new row, `renewedFromId` set)
- `POST /memberships/{id}/hold`, `/resume` -> MembershipDto
- `POST /memberships/{id}/cancel` `{reason}` (owner) -> MembershipDto

## Payments

`PaymentDto`: `{id, memberId, memberCode, memberName, membershipId|null, amountPaise, paidAt, method, receiptNo, status:"RECORDED|VOIDED", voidReason|null, recordedByName}`
Methods: `CASH, UPI, CARD_AT_COUNTER, BANK_TRANSFER, OTHER` (no card details are ever stored).

- `GET /payments?memberId=&from=&to=&status=&page=&size=` (canRecordPayments) -> Page<PaymentDto>
- `POST /payments` `{memberId, membershipId?, amountPaise, method, paidAt?, allowAdvance?}` (canRecordPayments) -> 201; 400 `OVERPAYMENT`
- `POST /payments/{id}/void` `{reason}` (owner) -> PaymentDto
- `GET /payments/{id}/receipt` (canRecordPayments) -> `text/html` printable receipt

## QR and attendance

- `GET /members/{id}/qr` (owner, staff) -> `{passId, status, createdAt}` or 404 `NO_ACTIVE_PASS` (never returns the token)
- `POST /members/{id}/qr/reissue` (owner, staff) -> `{passId, token, createdAt}` (old pass revoked immediately)
- `POST /qr/{passId}/revoke` (owner, staff) -> 204
- `POST /attendance/scan` `{token}` -> 200 `ScanResponse`
- `POST /attendance/manual` `{memberId}` -> 200 `ScanResponse`
- `GET /attendance?date=&memberId=&page=&size=` (owner, staff) -> Page<AttendanceDto>
  `{id, memberId, memberCode, memberName, checkedInAt, source:"QR|MANUAL", recordedByName}`; default date = today.

`ScanResponse`: `{result, message, member:{id,memberCode,fullName}|null, membershipEndDate|null, daysRemaining|null, checkedInAt|null}`
`result`: `CHECKED_IN, DUPLICATE_RECENT, EXPIRED, NO_MEMBERSHIP, NOT_STARTED, ON_HOLD, MEMBER_INACTIVE, PASS_REVOKED, UNKNOWN_TOKEN`.
Only `CHECKED_IN` writes an attendance row.

## Member portal (role MEMBER only; identity always from the session, no member id is ever accepted)

- `GET /me` -> `{memberCode, fullName, phone, email, joinDate, emergencyContact, status, canEditEmail}` (no staff notes)
- `PATCH /me` `{email}` -> same; 403 `FIELD_NOT_EDITABLE` unless the gym setting allows it
- `GET /me/membership` -> `{current: MembershipDto|null, history: MembershipDto[]}`
- `GET /me/attendance?page=&size=` -> `{items:[{id,checkedInAt,source}], page, size, total, visitsThisMonth, monthly:[{month:"2026-10", visits}]}` (monthly = last 6 months, oldest first)
- `GET /me/payments?page=&size=` -> Page<`{id, amountPaise, paidAt, method, receiptNo, status}`>
- `GET /me/payments/{id}/receipt` -> `text/html`
- `GET /me/dues` -> `{chargedPaise, paidPaise, duesPaise}`
- `GET /me/qr` -> `{token, createdAt}`; `POST /me/qr/reissue` -> same (old pass stops working immediately)

The QR code content is the raw `token` string only.

## Dashboard (owner; staff gets a limited view with no finance)

- `GET /dashboard/summary?from=&to=` -> `{date, activeMembers, expiringSoon, expired, todayCheckIns, expiringSoonDays, expiredWindowDays, collections: {from,to,totalPaise}|null, rules: {activeMembers, expiringSoon, expired, todayCheckIns, collections}}`
  (`collections` is null for staff; `rules` are plain-language descriptions of each number)
- `GET /dashboard/action-list` -> `{date, items:[{type:"EXPIRING|EXPIRED|OVERDUE_DUES|DROP_OFF", memberId, memberCode, memberName, phone, detail, rule}]}`
  (`OVERDUE_DUES` is owner only)
- `GET /dashboard/health` (owner) -> `{score, label, note, factors:[{name, weight, score, detail, rule}]}`
- `GET /dashboard/leakage` (owner) -> `[{key, title, value, valueType:"PAISE|COUNT", rule}]`

## Renewal Recovery Center (contact tracking only; nothing is ever sent)

- `GET /followups` (owner, staff) -> `{items:[{membershipId, memberId, memberCode, memberName, phone, kind:"EXPIRING|EXPIRED", endDate, daysToEnd, contactStatus, note, updatedAt|null}]}`
- `PUT /memberships/{id}/followup` `{contactStatus, note?}` -> 204
  `contactStatus`: `NOT_CONTACTED, CONTACTED, WILL_RENEW, NOT_INTERESTED, NO_ANSWER`

## Reports (owner, or staff with canViewReports). Query `from`, `to`, `page`, `size`; add `format=csv` for CSV of all rows.

- `GET /reports/attendance` -> `{from,to,totalVisits,uniqueMembers,daily:[{date,count}], rows: Page<AttendanceDto>}`
- `GET /reports/expiry` -> `{from,to,total, rows: Page<{membershipId,memberId,memberCode,memberName,phone,planName,endDate,status}>}`
  (memberships ending in the range that have not been followed by a later membership)
- `GET /reports/collections` -> `{from,to,totalPaise,paymentCount,voidedCount,voidedPaise,byMethod:[{method,totalPaise,count}], rows: Page<PaymentDto>}` (rows are RECORDED payments only)
- `GET /reports/outstanding` -> `{totalDuesPaise, memberCount, rows: Page<{memberId,memberCode,memberName,phone,chargedPaise,paidPaise,duesPaise}>}`

## Audit (owner)

- `GET /audit?action=&entityType=&page=&size=` -> Page<`{id, at, actorId, actorName, action, entityType, entityId, details}`>

## Operations

- `GET /actuator/health` (outside `/api/v1`) -> `{"status":"UP"}`
- Swagger UI at `/swagger-ui.html` (disabled in the prod profile).
