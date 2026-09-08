# MindCare

MindCare is a mental-health clinic ERP: appointment booking, clinical
documentation, billing, and staff administration for a therapy practice,
built around one hard rule — **a user's role is looked up fresh from the
database on every request**, never trusted from a client-supplied token or
form field.

- **Backend:** Spring Boot 3.5.x, Java 25, PostgreSQL. Deployed on Render.
- **Frontend:** Vanilla HTML/CSS/JS (no build step). Deployed on Vercel.
- **Repo:** `github.com/edutst6-a11y/online-therapy-system`, branch `mindcare-v2`.
  (The repo's `main` branch is a separate, older project — never merge
  `mindcare-v2` into it.)
- **Live:** https://online-therapy-system.vercel.app (frontend) talking to
  https://online-therapy-system.onrender.com (backend API).

## Roles

Every account has exactly one role, assigned at creation and changeable only
by staff with the right permission (see Super Admin below). The dashboard UI
adapts entirely to whichever role is logged in.

| Role | Can do |
|---|---|
| **Client** | Book a session with a therapist, track its status, message their therapist, view their own documents/invoices, fill in intake forms and assessments. |
| **Therapist** | Publish available times, approve/complete sessions booked against them, write and sign clinical notes, build treatment plans, share a Google Meet link for an approved session. |
| **Clinical Supervisor** | Read-only oversight of clinical notes — cannot create or edit them. |
| **Receptionist** | Approve, decline, or reschedule incoming appointment requests. No access to clinical notes or intake content — operational visibility only. |
| **Finance** | Create invoices, record payments, track balances and receipts. Only role besides Maintenance that can touch billing. |
| **Maintenance** | Clinic-wide visibility: every user and appointment, provisions new staff accounts, account lockout/enable controls, audit log access. |

There is no public sign-up path to any role except **Client** —
`POST /api/auth/register` always creates a Client account regardless of what
the request body claims. Every other role is provisioned by an existing
Maintenance account through `POST /api/staff`.

### Super Admin

Independent of role: a user can be flagged `superAdmin = true`, which lets
them switch their own *active* role at will (`PATCH /api/auth/active-role`)
without changing their underlying account role. This is how one trusted
person can act as, say, Finance one moment and Maintenance the next, without
juggling separate logins. Only an existing super admin can grant or revoke
the flag on another account (`PATCH /api/staff/{id}/super-admin`), and
revoking it immediately clears whatever active role they'd switched into —
proven by `SuperAdminGrantTest`.

## Video sessions

There's no backend Google Calendar/Meet integration. Once Reception or
Maintenance approves a booking, the therapist starts an ordinary Google Meet
call under their own Google account (`meet.google.com/new`) and pastes the
resulting link onto the appointment (`PATCH /api/appointments/{id}/meet-link`,
restricted to `^https://meet\.google\.com/.+`). The client then sees the
same link as a "Join Session" button. Rescheduling an appointment clears any
existing link, since it no longer matches the new time. Covered end to end
by `VideoSessionLinkTest`.

## Local development

Requires a local PostgreSQL instance and Java 25.

```bash
sudo -u postgres psql -c "CREATE ROLE mindcare WITH LOGIN PASSWORD 'your-local-password';"
sudo -u postgres psql -c "CREATE DATABASE mindcare OWNER mindcare;"
```

Create `backend/application.properties` (gitignored, auto-loaded by Spring
Boot over the committed defaults) with at least:

```properties
DB_PASSWORD=your-local-password
JWT_SECRET=<output of: python3 -c "import secrets,base64;print(base64.b64encode(secrets.token_bytes(48)).decode())">
SEED_MAINTENANCE_EMAIL=admin@mindcare.local
SEED_MAINTENANCE_PASSWORD=<a password you choose>
```

Then:

```bash
cd backend && mvn spring-boot:run
```

The first boot creates the seeded Maintenance account from those
`SEED_MAINTENANCE_*` values — that's your way in to provision every other
role through the UI. `ddl-auto=update` manages the schema; there are no
separate migration files to run by hand.

Serve `frontend/` with any static file server (e.g. `python3 -m http.server
4500`) — `mindcare.cors.allowed-origins` already permits `localhost:4500` and
`localhost:5500` by default.

### Tests

```bash
cd backend && mvn test
```

31 integration tests run the real Spring Security filter chain end to end
over HTTP (`TestRestTemplate`), against the same local Postgres database
used for manual dev work — there's no Docker/Testcontainers in play, and
`ddl-auto=update` plus randomly-suffixed test emails keep it non-destructive.
Coverage: auth and role-always-from-DB enforcement, account lockout,
super-admin grant/revoke, RBAC boundaries per resource, the full
booking→approve→complete flow and ownership checks, the Meet-link hand-off,
invoice/payment math, and clinical-note immutability (signed notes can only
be amended, never edited).

## Deployment

Both services deploy from the `mindcare-v2` branch; pushing to it
auto-deploys both.

- **Backend (Render):** Docker build (`backend/Dockerfile`) — `mvn package
  -DskipTests` compiles the jar, tests never run inside the build container.
  Configure via the Render service's Environment tab: `DB_URL` (or
  `DB_HOST`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`), `JWT_SECRET`,
  `SEED_MAINTENANCE_EMAIL`/`SEED_MAINTENANCE_PASSWORD` (only matters on the
  very first boot against an empty database), and `CORS_ALLOWED_ORIGINS`
  (must include the exact live Vercel origin — a stale or missing entry here
  silently breaks every request as a CORS preflight failure, not an obvious
  error message).
- **Frontend (Vercel):** static deploy of `frontend/`. Production must track
  the `mindcare-v2` branch under Project Settings → Git → Production Branch
  — it does not track it by default if the project was ever connected to a
  different branch.

If the deployed frontend looks stale after a push, it's almost always
browser cache, not a failed deploy — hard-refresh isn't always enough;
force a real reload of the CSS/JS (e.g. `?bust=<timestamp>` on the asset
URL) before concluding something's broken.

## Disaster recovery

- **Database.** Production Postgres is Aiven-hosted. Confirm directly in the
  Aiven console which backup/point-in-time-recovery window the current plan
  includes — this hasn't been independently verified from this environment
  and shouldn't be assumed.
- **Losing all Maintenance access.** If every Maintenance account is somehow
  locked out or deleted, `POST /api/bootstrap/maintenance` creates a fresh
  one — but only while zero Maintenance accounts exist in the database
  (`BootstrapController`); the instant one exists, it permanently returns
  409 and does nothing, so it can't be used to escalate privilege once the
  clinic is running normally.
- **Restoring from a database backup.** On next boot after a restore,
  `EnumConstraintFixupRunner` re-syncs every enum-backed `CHECK` constraint
  automatically, and `MaintenanceAccountSeeder` re-seeds the first
  Maintenance account only if none exists yet — so a restore to an empty or
  partial database won't leave the app unable to start or unable to log in.
- **Rotating the JWT secret.** Immediately invalidates every outstanding
  token (everyone gets logged out) — no other data is affected, since roles
  are always re-derived from the database rather than trusted from the
  token itself.

## Security notes

- Passwords: BCrypt. Five consecutive failed logins locks the account
  (`AuthSecurityTest.accountLocksAfterFiveConsecutiveFailures`).
- JWTs carry only the user id and expire after `JWT_EXPIRATION_MINUTES`
  (default 1440 = 24h); every request re-derives role and permissions fresh
  from the database (`JwtAuthFilter`) rather than trusting anything the
  token claims.
- CORS is locked to an explicit origin allowlist (`CORS_ALLOWED_ORIGINS`),
  not a wildcard.
- Client documents are stored as bytea in Postgres (no filesystem path ever
  built from a user-supplied filename), capped at 5MB, and always served
  with `Content-Disposition: attachment` — never rendered inline — so a
  malicious content-type on an uploaded file can't become stored XSS.
- Dependencies are checked periodically against upstream CVE advisories;
  Spring Boot tracks the latest patch release of its minor line (currently
  pinned to 3.5.16, the final release of the now-EOL 3.5.x branch — a move
  to a supported major line is a larger decision to make deliberately, not
  something to do silently in the background).
