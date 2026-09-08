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

## Using MindCare, role by role

A walkthrough of what each role actually does day to day, once logged in at
the live site.

**As a Client:** Fill in your intake form once (Your Intake panel) — it asks
for basics like emergency contact and why you're seeking care, and requires
ticking consent before it'll save. Book a session from the booking panel by
picking a therapist and one of their open times; it starts as *Pending*
until Reception approves it. Once approved, a **Join Session** button
appears on that appointment as soon as your therapist has posted a Meet
link. You can message your therapist, see any documents shared with you,
and view your invoices/receipts under Billing, all from the same dashboard.

**As a Therapist:** Publish the times you're free in the Availability panel
— clients can only book against times you've published. Once Reception
approves a booking, start a Google Meet call yourself
(`meet.google.com/new`) and paste the link into that appointment so your
client can join. After a session, mark it Complete. Clinical Notes: write a
draft, and once you sign it, it's permanently locked — to correct a signed
note later, use Amend, which opens a new draft linked to the original
rather than editing it. Treatment Plans let you track goals per client over
time, separate from individual session notes.

**As a Receptionist:** Your dashboard is the incoming-requests queue —
approve, decline, or reschedule each pending booking. You intentionally
cannot see clinical notes or intake answers; you get names, times, and
status only.

**As Finance:** Build an invoice against a client (with line items and an
optional discount — the total is always computed server-side, never trust a
number typed into the browser). Record payments against it as they come in;
the status moves from Unpaid → Partially Paid → Paid automatically as
payments are recorded, and each payment issues its own numbered receipt.
Reports give you totals invoiced/collected/outstanding.

**As Maintenance:** You see every user and every appointment clinic-wide.
Provision new staff accounts (this is the only way anyone gets a role other
than Client), lock/unlock or enable/disable accounts, and review the audit
log — a record of who did what, when, from where, and whether it succeeded,
kept for every state-changing action in the system.

**As a super admin** (currently one account, per the project's original
brief): a role switcher appears in the nav letting you act as any role
without a separate login — useful for QA and for stepping in wherever
staffing is thin. Granting this to someone else is done from the
Maintenance user table's Grant/Revoke control, and only an existing super
admin can grant it further.

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

- **Database.** Production Postgres is Aiven-hosted, on Aiven's **free plan**
  (`pg-20bb9a7e`, 1 CPU / 1GB RAM / 1GB storage) — a genuinely free tier, not
  the account's separate 28-day/$50 trial credit (that credit only applies
  to non-free-plan services, so it isn't a countdown against this database).
  Confirmed directly in the Aiven console: daily automated backups are
  running, with point-in-time recovery available in between them. As of this
  check the service was only 2 days old, so only ~2-3 days of backup history
  existed to inspect — the actual retention *cap* on the free plan wasn't
  independently confirmed beyond that. Two real gaps worth acting on
  deliberately, not silently:
  - **The free plan auto-powers-off during inactivity** (Aiven's own upgrade
    prompt says so directly) — separate from and in addition to Render's
    free-tier backend spin-down. Both halves of the stack can go cold
    independently, which affects real availability, not just cold-start
    latency.
  - **1GB storage is a hard ceiling** — fine today, but worth revisiting as
    client documents (5MB cap each) and clinical records accumulate.
  Aiven offers a $5/month tier that removes the inactivity power-off and
  adds basic support; whether that trade-off is worth it for a live system
  holding real client health data is a call for whoever owns the budget,
  not something to flip silently.
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
