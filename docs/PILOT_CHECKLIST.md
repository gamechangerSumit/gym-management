# Pilot checklist, terms notes, limitations

## Before go-live
- [ ] `.env` filled; secrets stored in a password manager; `APP_QR_ENC_KEY` backed up separately
- [ ] HTTPS working; `APP_COOKIE_SECURE=true`; owner password changed from the bootstrap value
- [ ] Time zone and thresholds checked in Settings
- [ ] Plans created; staff accounts created with least-privilege permissions
- [ ] Backup ran; **restore test passed**
- [ ] Camera scan tested on the actual reception device; manual check-in tested as fallback
- [ ] Test member invited, activated, QR scanned, receipt viewed; member cannot see staff screens
- [ ] Run the e2e suite (`e2e/`) against the stack

## Terms-of-use / privacy notes (draft points for your own lawyer to review — not legal advice)
Tell members what you store (name, phone, optional email, emergency contact, attendance, payments); that card/bank details are never
stored; who can see their data (owner, permitted staff); how to request correction; retention policy you choose.

## Known limitations
- No email/SMS: owner shares invite and reset links in person; follow-ups are call-and-record only.
- Receipts are HTML/JSON (printable), not PDF.
- One gym per deployment (tables carry `gym_id` for later expansion).
- JdbcClient + SQL instead of JPA; BCrypt instead of Argon2 (documented deviations from the plan).
- No offline mode; scanning needs a camera and HTTPS.

## Verification status of this delivery
See README "What was and was not verified".
