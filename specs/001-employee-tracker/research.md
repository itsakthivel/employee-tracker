# Research: Employee Tracker

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-12 (revised after the clarification session)

Every Technical Context unknown is resolved below. The stack (Java Spring + React) was chosen by the user. The other decisions follow from it and from the spec.

## 1. Backend language and framework

- **Decision**: Java 21 (LTS) with Spring Boot 3.3.x (Web MVC, Data JPA, Security, Validation, Mail, Session JDBC), built with Maven.
- **Rationale**: The user chose Spring. Spring Boot 3 requires Java 17+, and 21 is the current LTS. Maven 3.9 is already installed. Blocking MVC fits a CRUD app with ~5,000 records.
- **Alternatives considered**: WebFlux (rejected: unnecessary complexity); Gradle (rejected: Maven already present).
- **Environment note**: The machine has JDK 11. JDK 21 must be installed.

## 2. Storage

- **Decision**: PostgreSQL 16, with migrations managed by Flyway.
- **Rationale**: Relational data with uniqueness constraints, foreign keys and a self-referencing reporting hierarchy that recursive CTEs handle well.
- **Alternatives considered**: MySQL (weaker recursive-CTE and partial-index support); H2 for tests (rejected: diverges from Postgres; Testcontainers used instead).

## 3. Search and filtering (FR-016–FR-018, SC-003)

- **Decision**: Server-side case-insensitive partial matching (`ILIKE '%term%'`) on name, employee number and email, backed by `pg_trgm` GIN indexes. Filters and sorts are built with JPA Specifications. Results are paginated (default 25).
- **Rationale**: Searching 5,000 rows takes milliseconds, well within the 1-second goal.
- **Alternatives considered**: Elasticsearch (rejected: operational cost); `tsvector` full-text search (rejected: poor at partial IDs and emails).

## 4. Roles and field visibility (FR-003, FR-006, FR-006b, FR-019)

- **Decision**: Two roles, `ADMIN` and `EMPLOYEE`. Every response about an employee is built as one of three views, chosen by a single `EmployeeViewPolicy`:
  - **Directory view**, for an `EMPLOYEE` viewing someone else: full name, job title, department, location, manager, direct reports, work email and phone. Only `ACTIVE`/`ON_LEAVE` employees are visible; `TERMINATED` are hidden (returned as 404).
  - **Self view**, for an `EMPLOYEE` viewing their own record: the directory fields plus employee number, employment type, status, start date, date of birth, home address and emergency contact. It excludes notes and all sensitive data.
  - **Admin view**: every field. Sensitive data is always masked here, and full values are available only through the reveal endpoint (§6).
- **Rationale**: One policy class that every endpoint uses is easy to test exhaustively (SC-009) and prevents a field leaking through a forgotten serializer. Separate DTOs per view (not `@JsonView` on entities) make it impossible to serialize sensitive fields by accident.
- **Change history (FR-009)**: Admins see the full history. An employee can see the history of their own record, limited to fields in the self view. Directory users cannot see other employees' history.
- **Search, filter and sort for `EMPLOYEE`**: the same as for admins, but limited to directory fields and active/on-leave records. The status filter accepts only `ACTIVE`/`ON_LEAVE`, and `includeTerminated` is ignored.
- **Search by employee ID (FR-016 vs FR-003)**: Employee number is not a directory field, so for `EMPLOYEE` the search term matches name and work email only. Admins also match employee number. This resolves the overlap between the two requirements in favor of FR-003's field list.
- **Alternatives considered**: A manager role (dropped in clarification); field-level `@JsonView` (rejected: easy to misconfigure for sensitive data).

## 5. Sensitive data storage (FR-006a, FR-006e, FR-006f)

- **Decision**:
  - Sensitive data is kept in separate tables (`employee_sensitive`, one per employee; `compensation_record`, many per employee), never in the `employee` table. That way, no general employee query can load it by accident.
  - Application-level field encryption with AES-256-GCM via a JPA `AttributeConverter`, applied to: government ID number, tax ID, bank account number, bank routing code, account holder name and compensation amount. Each ciphertext stores a key id, so keys can be rotated. The key comes from an environment variable or secret store (`TRACKER_DATA_KEY_<id>`), never from the database or the repository.
  - The last 4 characters of each maskable value are stored in plaintext (`*_last4` columns), so masked display needs no decryption.
  - Defense in depth: TLS for browser↔app and app↔database traffic, and encrypted disks/backups at the hosting level.
- **Rationale**: Encrypting at the field level means a database dump or backup alone does not expose sensitive values (FR-006f), which disk encryption alone does not guarantee. A separate table keeps the blast radius small.
- **Trade-off**: Encrypted values cannot be searched, sorted or summed in SQL. The spec needs none of that for sensitive data.
- **Alternatives considered**: PostgreSQL `pgcrypto` (rejected: the key would be passed in SQL and could appear in logs); a Vault transit engine (deferred: extra infrastructure; the converter is designed so it can be swapped in later).

## 6. Masking, reveal and access logging (FR-006c, FR-006d, SC-008)

- **Decision**: `GET /employees/{id}/sensitive` returns masked values only (`••••1234`). `POST /employees/{id}/sensitive/reveal` with a `field` name returns that one full value. Every reveal and every create/update of sensitive data writes a `sensitive_access_log` row in the same transaction: who, employee, field, action (`REVEAL`, `CREATE`, `UPDATE`) and timestamp. Change-history rows for masked fields store the masked form only. Reveal responses send `Cache-Control: no-store`.
- **Rationale**: A separate POST makes each reveal an explicit, countable event. Writing the log in the same transaction guarantees that 100% of events are logged (SC-008).
- **Alternatives considered**: Revealing all fields at once (rejected: weaker audit trail).

## 7. Authentication (FR-003a–FR-003f)

- **Decision**:
  - Spring Security with a JSON login endpoint and server-side sessions stored in PostgreSQL via **Spring Session JDBC**. The session cookie is HTTP-only, Secure and SameSite=Lax, and CSRF protection uses a double-submit token.
  - Passwords are hashed with BCrypt (cost 12). Rules: at least 12 characters, and not in a bundled list of the 100,000 most common passwords (from the SecLists project), checked offline.
  - Idle timeout: 30 minutes (`spring.session.timeout`).
  - Lockout: the account stores `failed_attempts` and `locked_until`. After 5 consecutive failures it is locked for 15 minutes, and a success resets the counter. Login error messages are the same whether or not the username exists.
  - First sign-in: accounts created or reset by an admin carry `must_change_password=true`. A security filter rejects all API calls except `/auth/me`, `/auth/change-password` and `/auth/logout` with `403 PASSWORD_CHANGE_REQUIRED` until the password is changed.
  - Forgot password: `POST /auth/password-reset` always returns 202. If the account exists and is enabled, a 32-byte random token is emailed as a link. Only its SHA-256 hash is stored, with a 1-hour expiry, and it is single-use. Completing the reset clears the lockout.
  - Session revocation: on password change or reset, termination, or an admin disabling an account, all of that user's sessions are deleted. Spring Session's `FindByIndexNameSessionRepository` supports finding sessions by user. This makes FR-003a take effect immediately.
  - Email is sent over SMTP via Spring Mail. In development, a Mailpit container captures emails.
- **Rationale**: Server-side sessions are needed so a terminated employee's access ends immediately and doesn't wait for a token to expire. Checking passwords against an offline list avoids sending password-derived data to a third party.
- **Alternatives considered**: JWT (rejected: sessions can't be revoked on termination); checking the HaveIBeenPwned API (rejected: external dependency and a privacy question); Argon2 (fine, but BCrypt is Spring's default and sufficient).
- **Accepted risk (from spec)**: Admins have no second factor. The design keeps the login flow behind one `AuthenticationProvider`, so adding TOTP later is a local change.

## 8. Change history (FR-008, FR-009, SC-004)

- **Decision**: An explicit `employee_change` table, written in the same transaction as each change by one service method that diffs old and new values field by field. Sensitive fields record masked values.
- **Alternatives considered**: Hibernate Envers (rejected: stores row snapshots and is awkward to filter by view); database triggers (rejected: cannot see the application user).

## 9. Concurrent edits

- **Decision**: Optimistic locking with JPA `@Version`. A stale version gets `409 STALE_VERSION`, and the UI offers to reload.

## 10. Reporting-chain integrity (FR-010, FR-014)

- **Decision**: A recursive CTE rejects a manager assignment if the new manager is the employee themselves or is somewhere below them in the reporting tree. Terminating an employee with direct reports returns `409 DIRECT_REPORTS` with the list of reports unless `acknowledgeDirectReports=true` is sent. Reports are not reassigned automatically.

## 11. Export and headcount (FR-020, FR-021)

- **Decision**: Admin-only. Export is CSV (UTF-8 with BOM, RFC 4180 quoting), streamed from the same Specification as the list. Sensitive fields are never included (FR-006b). Cells starting with `=`, `+`, `-` or `@` are prefixed with `'` to stop formula injection.

## 12. Frontend

- **Decision**: React 18 + TypeScript built with Vite, plus React Router, TanStack Query, React Hook Form + Zod and Mantine. The UI hides admin controls for `EMPLOYEE`, but the server is the only real enforcement. For production, the build is bundled into the Spring Boot jar (same origin, no CORS).
- **Alternatives considered**: Next.js (SSR is unneeded behind login); a separate frontend deployment (needs CORS and cross-site cookies).

## 13. Testing

- **Decision**: JUnit 5 + Spring Boot Test, Testcontainers (PostgreSQL 16, Mailpit), and MockMvc contract tests against `contracts/openapi.yaml`. A role × endpoint × field visibility test matrix covers SC-009. Vitest + React Testing Library for components, and Playwright end-to-end tests with one journey per user story.

## 14. API style

- **Decision**: JSON REST under `/api/v1`, documented in OpenAPI 3.1. Errors use RFC 9457 Problem Details with a machine-readable `code` and `fieldErrors`. Live docs are served by springdoc-openapi in the dev profile only.
