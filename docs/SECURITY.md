# Security notes

**Authentication** — BCrypt (strength 12) passwords, min 8 chars with letter+digit; server-side sessions in PostgreSQL
(HttpOnly, SameSite=Lax, Secure in prod); CSRF double-submit (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header); lockout after
5 failures for 15 min; per-IP and per-account rate limits (429 + Retry-After); generic login errors with timing equalization;
account status and forced password change re-checked on every request.

**Authorization** — URL rules plus method-level `@PreAuthorize`; roles OWNER / STAFF (4 fine-grained permissions) / MEMBER.
Member endpoints (`/api/v1/me/**`) take identity from the session only and use separate DTOs (no staff notes, no other members).

**Tokens** — invite/reset/QR tokens: 256-bit random, only SHA-256 hashes used for lookup; invite/reset are single-use and expire.
QR passes keep an AES-256-GCM copy so the member can re-display them (decision: "Option A"); the QR payload contains no personal data.

**Data integrity** — money in integer paise; overlapping memberships blocked by a PostgreSQL exclusion constraint; payments
voided, never deleted; audit log append-only by trigger; dues are always computed, never stored.

**Hardening** — strict JSON (unknown fields rejected), no stack traces in responses, log scrubbing, security headers in nginx,
Swagger disabled in prod, containers run as non-root.

**Not done / out of scope** — MFA, email/SMS delivery, Argon2, field-level DB encryption, WAF, penetration test. A professional
security review is recommended before handling real members' data at scale.
