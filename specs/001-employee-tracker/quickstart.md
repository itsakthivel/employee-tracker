# Quickstart & Validation Guide: Employee Tracker

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/openapi.yaml](./contracts/openapi.yaml) | **Data model**: [data-model.md](./data-model.md)

This guide shows how to run the app and confirm that each user story and the security requirements work from start to finish. It describes expected behavior only. Implementation belongs in `tasks.md`.

## Prerequisites

| Tool | Version | Note |
|------|---------|------|
| JDK | 21 | The machine has JDK 11. Install 21 with `brew install openjdk@21` and set `JAVA_HOME`. |
| Maven | 3.9+ | Installed (3.9.10). The project also ships `./mvnw`. |
| Node.js | 20+ | Installed (v22). |
| Docker | Recent | Runs PostgreSQL and Mailpit for development, and Testcontainers. |

## Setup

```bash
# 1. Start PostgreSQL 16 (db/user/pass: tracker) and Mailpit (SMTP :1025, inbox UI http://localhost:8025)
docker compose up -d
# If port 5432 is already taken: TRACKER_DB_PORT=55432 docker compose up -d
#   and export DB_URL=jdbc:postgresql://localhost:55432/tracker before step 3

# 2. Set the data-encryption key (dev only; production uses a secret store)
export TRACKER_DATA_KEY_ID=dev1
export TRACKER_DATA_KEY_dev1=$(openssl rand -base64 32)   # keep it: the seeded sensitive data is encrypted with it

# 3. Backend (applies Flyway migrations; the "dev" profile seeds sample data and accounts)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080

# 4. Frontend (second terminal; proxies /api to :8080)
cd frontend
npm install
npm run dev                                                # http://localhost:5173
```

The dev seed creates:
- about 60 employees across 4 departments, with a 3-level reporting chain, some On Leave and Terminated employees, and sensitive data and compensation history for a few of them
- `admin@example.com` (ADMIN)
- `alice@example.com` (EMPLOYEE, "Alice Morgan", a team lead with direct reports)
- `newhire@example.com` (EMPLOYEE, with a temporary password, so the password must be changed)
- `former@example.com` (EMPLOYEE, linked to a Terminated employee, so the account is disabled)

All seeded passwords are `ChangeMe-12345`. API docs are at http://localhost:8080/swagger-ui.html (dev profile only).

## Automated checks

```bash
cd backend  && ./mvnw verify          # unit + Testcontainers integration + contract + visibility-matrix tests
cd frontend && npm test               # component tests
cd frontend && npx playwright install chromium   # once
cd frontend && npx playwright test    # end-to-end; needs the full stack running with the dev seed
```

Expected result: all suites pass. The **visibility matrix** test (role × endpoint × field) must show no sensitive field in any non-admin response (SC-009).

## Manual validation scenarios

### Sign-in and accounts (FR-003a–f)

1. Sign in as `newhire@example.com`. **Expected**: you must change the password before reaching any other page. A 10-character password or `Password1234` is rejected.
2. Enter a wrong password for `alice@example.com` 5 times. **Expected**: the 6th attempt with the correct password is refused for 15 minutes. The message doesn't say whether the account exists, and an unknown username gets the same message.
3. Choose **Forgot password** for Alice. **Expected**: a reset email appears in Mailpit. The link works once, and using it again fails. Requesting a reset for `nobody@example.com` shows the same confirmation, and no email is sent.
4. Sign in as Alice, leave the tab idle for 30 minutes, then click anything. **Expected**: you're sent back to the sign-in page.
5. As admin, create a sign-in for an employee who doesn't have one. **Expected**: the username is their work email, and the first sign-in requires a password change.

### US1: Add and maintain employee records (P1), as admin

1. Add an employee with all required fields. **Expected**: the profile shows every value, and the history shows `CREATED`.
2. Leave the email empty or enter `not-an-email`, and reuse an existing employee number. **Expected**: nothing is saved, and the errors are shown per field.
3. Edit the job title. **Expected**: the history shows `jobTitle: old → new`, admin and the timestamp.
4. Open the same employee in two tabs and save in both. **Expected**: the second save is told the record changed.
5. Make an employee's manager one of their own reports. **Expected**: rejected as a reporting cycle.
6. **Sensitive data**: enter a bank account number, government ID and a compensation record. **Expected**: the profile shows `••••` and the last 4 characters. **Reveal** shows the full number, and **Sensitive access log** lists `CREATE` entries and the `REVEAL`. The employee's history shows only masked values.

### US2: Find and view employees (P2)

1. As Alice, search part of a name and filter by department. **Expected**: matching active and on-leave employees appear within 1 second.
2. As Alice, open a colleague's profile. **Expected**: only name, title, department, location, manager, direct reports, email and phone are shown. There's no date of birth, address, emergency contact, notes or sensitive data, and no edit controls.
3. As Alice, open a Terminated employee's profile by URL. **Expected**: not found.
4. As Alice, open **My profile**, change the phone number and emergency contact, and save. **Expected**: the change is saved, and the history shows `SELF_UPDATED`. Other fields can't be edited.
5. Search for `zzzz`. **Expected**: "No employees found" and a **Clear filters** action.

### US3: Status changes and offboarding (P3), as admin

1. Change Alice to On Leave and back. **Expected**: both changes appear in the history.
2. Terminate Alice with an end date while she's signed in in another browser. **Expected**: her session ends at once, she can't sign in again, and she's hidden from the directory for other employees.
3. Terminate the seeded Engineering manager. **Expected**: a warning lists their direct reports, and termination happens only after you confirm.
4. Enter an end date before the start date. **Expected**: rejected.
5. Reactivate Alice. **Expected**: status Active and end date cleared. She can sign in, but must change her password.

### US4: Headcount and export (P4)

1. As admin, open **Overview**. **Expected**: the totals match the list filtered the same way.
2. As admin, export a filtered list. **Expected**: the CSV has exactly the filtered rows, has no sensitive columns, and shows names correctly in a spreadsheet.
3. As Alice, try `/api/v1/reports/headcount` and `/api/v1/employees/export`. **Expected**: 403 for both.

### Encryption at rest (FR-006f)

1. Run `docker compose exec postgres psql -U tracker -c "select gov_id_number, bank_account_number from employee_sensitive limit 3"`. **Expected**: unreadable binary ciphertext, not the numbers.
2. Restart the backend with a wrong `TRACKER_DATA_KEY_dev1`. **Expected**: masked values still display (from the stored last 4 characters), but reveal fails safely with an error, and nothing is decrypted wrongly.

## Success-criteria spot checks

| Criterion | How to check |
|-----------|--------------|
| SC-001 (add in < 3 min) | Time US1 step 1 with a first-time admin |
| SC-002 / SC-003 | Load the 5,000-employee perf seed (`dev,perf` profiles) and repeat US2 step 1 |
| SC-004 (100% history) | Integration test: every mutating endpoint writes a history row |
| SC-005 (headcount exact) | US4 step 1 plus an integration test |
| SC-007 (no duplicates) | Unique indexes + US1 step 2 |
| SC-008 (100% logged) | Integration test: every reveal and sensitive write adds exactly one log row |
| SC-009 (0 leaks) | Visibility-matrix test plus US2 step 2 and US4 step 3 |
