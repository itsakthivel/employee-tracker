# Employee Tracker

HR administrators keep employee records and run the employment lifecycle: hire, leave, terminate and rehire. Every employee can sign in to browse a company directory and update their own phone number and emergency contact.

Sensitive data (compensation, government ID, tax ID and bank details) is visible only to administrators. It is encrypted in the database, masked on screen until an administrator reveals it, and every reveal and change is logged.

- Spec, plan and tasks: [`specs/001-employee-tracker/`](specs/001-employee-tracker/)
- API contract: [`specs/001-employee-tracker/contracts/openapi.yaml`](specs/001-employee-tracker/contracts/openapi.yaml)
- Run and validation guide: [`specs/001-employee-tracker/quickstart.md`](specs/001-employee-tracker/quickstart.md)

## Stack

| Part | Technology |
|------|------------|
| Backend | Java 21, Spring Boot 3.3 (Web MVC, Data JPA, Security, Session JDBC, Mail), Flyway |
| Database | PostgreSQL 16 |
| Frontend | React 18, TypeScript, Vite, Mantine, TanStack Query, React Hook Form + Zod |
| Tests | JUnit 5 + Testcontainers + OpenAPI contract checks; Vitest; Playwright |

`backend/` and `frontend/` are separate projects. A production build packages the frontend into the backend jar, so the app runs as one process with one origin.

## Prerequisites

- **JDK 21.** Spring Boot 3 needs 17 or later; the build enforces 21.
- **Docker.** Runs PostgreSQL and Mailpit locally, and Testcontainers in the tests.
- **Node.js 20+.** Only needed for frontend development; the jar build downloads its own copy.

## Run locally

See the [quickstart](specs/001-employee-tracker/quickstart.md) for the full walkthrough and seeded accounts. In short:

```bash
docker compose up -d                       # set TRACKER_DB_PORT=55432 if 5432 is taken
export TRACKER_DATA_KEY_ID=dev1
export TRACKER_DATA_KEY_dev1=$(openssl rand -base64 32)   # keep this value: it decrypts your dev data
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev)
(cd frontend && npm install && npm run dev)  # http://localhost:5173
```

## Tests

```bash
cd backend  && ./mvnw verify          # 61 integration tests (needs Docker)
cd frontend && npm test               # component tests
cd frontend && npx playwright test    # end-to-end; needs the dev stack running with seed data
```

## Build and deploy

```bash
cd backend && ./mvnw -Pbundle-frontend package
java -jar backend/target/employee-tracker-1.0.0-SNAPSHOT.jar
```

Serve it behind HTTPS. Session cookies are marked `Secure` outside the `dev` profile.

### Production environment variables

| Variable | Purpose |
|----------|---------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection. Use TLS, for example `jdbc:postgresql://host/db?sslmode=require`. |
| `TRACKER_DATA_KEY_ID` | Id of the active data-encryption key (letters, digits, `_`). |
| `TRACKER_DATA_KEY_<id>` | Base64 of 32 random bytes (`openssl rand -base64 32`). Keep every key that has ever been active (see below). |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP for password-reset emails. |
| `TRACKER_MAIL_FROM` | Sender address for those emails. |
| `TRACKER_APP_BASE_URL` | Public URL of the app, used in reset links. |
| `TRACKER_BOOTSTRAP_ADMIN_EMAIL`, `TRACKER_BOOTSTRAP_ADMIN_PASSWORD` | Creates the first administrator on startup if none exists. Remove them once that account exists. |

### Encryption keys: backup and rotation

The data-encryption key is not stored in the database. **If you lose it, you lose every stored government ID, tax ID, bank detail and salary.** Back it up in your secret manager, separately from the database backups.

To rotate the key:

1. Generate a new key and add it as `TRACKER_DATA_KEY_<newid>`.
2. Set `TRACKER_DATA_KEY_ID=<newid>` and restart. New writes use the new key.
3. **Keep the old `TRACKER_DATA_KEY_<oldid>` configured.** Each stored value records which key encrypted it, so older values still decrypt. A record is re-encrypted with the active key the next time its sensitive data is saved.

If a key goes missing, masked values still display (they are stored separately), but Reveal returns an error rather than wrong data.

## Accepted risks (from the spec)

- **No second sign-in factor in v1.** Administrators can reveal bank and ID data with a password alone. This is partly offset by lockout, a 30-minute idle timeout, masking and reveal logging. Adding a one-time code for administrators is the recommended follow-up.
- **Sensitive data is kept indefinitely.** Former employees' records, including bank and ID details, are never erased in v1.
- **Legal review before go-live.** Confirm which data-protection law applies (consent, retention, breach notification) before storing real employee data.
- **No bulk import.** Every employee and sign-in is entered by hand.
