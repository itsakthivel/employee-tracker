---

description: "Task list for the Employee Tracker feature"
---

# Tasks: Employee Tracker

**Input**: Design documents from `/specs/001-employee-tracker/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/openapi.yaml](./contracts/openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: Included, but only where the spec or plan needs them for verification:
- SC-004 (100% history), SC-008 (100% of sensitive access logged) and SC-009 (0 sensitive values visible to non-admins in access testing) can only be shown by automated tests.
- The plan makes `contracts/openapi.yaml` the source of truth, enforced by contract tests.

Write each story's tests first and confirm they fail before implementing.

**Organization**: Tasks are grouped by user story, so each story can be built and tested independently. Sign-in and account management are Foundational, because every story needs them.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on unfinished tasks)
- **[Story]**: US1–US4, matching spec.md
- Paths are relative to the repository root. `BE` below always means `backend/src/main/java/com/example/tracker` and `BT` means `backend/src/test/java/com/example/tracker`; each task spells out the full path.

## Path Conventions

- Backend: `backend/src/main/java/com/example/tracker/<feature-package>/`, resources in `backend/src/main/resources/`, tests in `backend/src/test/java/com/example/tracker/`
- Frontend: `frontend/src/`, tests in `frontend/tests/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project skeletons, build and local infrastructure

- [X] T001 Create the top-level layout from plan.md: `backend/`, `frontend/`, `docker-compose.yml`, and a root `.gitignore` covering `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `.env`, `*.log`
- [X] T002 Create `backend/pom.xml` with these settings:
  - Parent `spring-boot-starter-parent` 3.3.x, `java.version` 21.
  - Dependencies: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-mail`, `spring-session-jdbc`, `flyway-core`, `flyway-database-postgresql`, `postgresql` (runtime), `springdoc-openapi-starter-webmvc-ui` 2.x.
  - Test dependencies: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `com.atlassian.oai:swagger-request-validator-mockmvc`.
  - `maven-enforcer-plugin` with `requireJavaVersion` `[21,)`.
- [X] T003 Add the Maven wrapper (`backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`) pinned to Maven 3.9.x
- [X] T004 Create `backend/src/main/java/com/example/tracker/TrackerApplication.java` (`@SpringBootApplication`, `@EnableJpaAuditing`)
- [X] T005 Create `backend/src/main/resources/application.yml` with:
  - Datasource from env (`DB_URL`, `DB_USER`, `DB_PASSWORD`) and `spring.jpa.hibernate.ddl-auto=validate`.
  - `spring.flyway.enabled=true`.
  - `spring.session.store-type=jdbc`, `spring.session.timeout=30m`, `spring.session.jdbc.initialize-schema=never`.
  - `server.servlet.session.cookie.http-only=true`, `secure=true`, `same-site=lax`.
  - `spring.mail.*` from env.
  - Properties `tracker.data-key.active-id` (`${TRACKER_DATA_KEY_ID}`), `tracker.app-base-url` and `tracker.mail.from`.
  - springdoc disabled by default.
- [X] T006 [P] Create `backend/src/main/resources/application-dev.yml` with localhost Postgres (`tracker`/`tracker`/`tracker`), Mailpit SMTP `localhost:1025`, `cookie.secure=false`, springdoc enabled at `/swagger-ui.html`, and `tracker.app-base-url=http://localhost:5173`
- [X] T007 [P] Create `docker-compose.yml` with `postgres:16` (db, user and password `tracker`; port 5432; named volume) and `axllent/mailpit` (ports 1025 and 8025)
- [X] T008 [P] Scaffold `frontend/` with Vite React-TS (`frontend/package.json`, `frontend/tsconfig.json`, `frontend/index.html`, `frontend/src/main.tsx`):
  - Dependencies: `react@18`, `react-dom@18`, `react-router-dom@6`, `@tanstack/react-query@5`, `react-hook-form`, `zod`, `@hookform/resolvers`, `@mantine/core`, `@mantine/hooks`, `@mantine/dates`, `@mantine/notifications`, `dayjs`.
  - Dev dependencies: `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom`, `@playwright/test`, `openapi-typescript`, `eslint`, `prettier`.
- [X] T009 [P] Create `frontend/vite.config.ts`: proxy `/api` to `http://localhost:8080` (with `cookieDomainRewrite: "localhost"`), Vitest `environment: "jsdom"`, test include `tests/unit/**/*.test.tsx`
- [X] T010 [P] Configure formatting and linting: the Spotless plugin (google-java-format) in `backend/pom.xml`; `frontend/.eslintrc.cjs` and `frontend/.prettierrc` with npm scripts `lint` and `format`
- [X] T011 [P] Add the npm script `gen:api` in `frontend/package.json`: `openapi-typescript ../specs/001-employee-tracker/contracts/openapi.yaml -o src/api/schema.d.ts`. Run it once and commit `frontend/src/api/schema.d.ts`.
- [X] T012 Configure `frontend-maven-plugin` in `backend/pom.xml` to run `npm ci` and `npm run build` in `../frontend`, and `maven-resources-plugin` to copy `../frontend/dist` into `target/classes/static`. Both run only under the Maven profile `bundle-frontend`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, core entities, error handling, sign-in and account management. Every story needs these.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete.

### Schema and core entities

- [X] T013 Create `backend/src/main/resources/db/migration/V1__core.sql`. It must contain:
  - `create extension if not exists pg_trgm`.
  - Tables `department`, `location`, `employee`, `user_account`, `password_reset_token` and `employee_change`, exactly as in data-model.md, with UUID ids and `created_at`/`updated_at` timestamptz.
  - Unique indexes on `lower(employee_number)`, `lower(work_email)`, `lower(department.name)`, `lower(location.name)`, `lower(user_account.username)` and `user_account.employee_id`.
  - Btree indexes on `employee(department_id)`, `(location_id)`, `(manager_id)` and `(status)`.
  - GIN `gin_trgm_ops` indexes on `employee(full_name)`, `(employee_number)` and `(work_email)`.
  - Index `employee_change(employee_id, changed_at desc)`.
  - Check constraints: `status in ('ACTIVE','ON_LEAVE','TERMINATED')`; `employment_type in ('FULL_TIME','PART_TIME','CONTRACTOR','INTERN')`; `(status = 'TERMINATED') = (end_date is not null)`; `end_date is null or end_date >= start_date`; `manager_id <> id`; `role in ('ADMIN','EMPLOYEE')`; `role <> 'EMPLOYEE' or employee_id is not null`.
- [X] T014 [P] Create `backend/src/main/resources/db/migration/V2__spring_session.sql` with the PostgreSQL `spring_session` and `spring_session_attributes` tables and indexes, copied from Spring Session JDBC's `schema-postgresql.sql`
- [X] T015 [P] Create `backend/src/main/java/com/example/tracker/common/BaseEntity.java`: a `@MappedSuperclass` with `UUID id` (generated), `@CreatedDate Instant createdAt` and `@LastModifiedDate Instant updatedAt`
- [X] T016 [P] Create the enums `EmploymentStatus` (`ACTIVE`, `ON_LEAVE`, `TERMINATED`) and `EmploymentType` (`FULL_TIME`, `PART_TIME`, `CONTRACTOR`, `INTERN`) in `backend/src/main/java/com/example/tracker/employee/`, and `Role` (`ADMIN`, `EMPLOYEE`) in `backend/src/main/java/com/example/tracker/account/Role.java`
- [X] T017 [P] Create the `Department` entity (`name` "Unique (case-insensitive), 1–100"; optional `head` ManyToOne Employee) and `DepartmentRepository` in `backend/src/main/java/com/example/tracker/reference/`
- [X] T018 [P] Create the `Location` entity (`name` "Unique (case-insensitive), 1–100") and `LocationRepository` in `backend/src/main/java/com/example/tracker/reference/`
- [X] T019 Create the `Employee` entity in `backend/src/main/java/com/example/tracker/employee/Employee.java` with every field from data-model.md and its Bean Validation constraints:
  - `employeeNumber`: `@Pattern("^[A-Za-z0-9-]{1,20}$")`.
  - `fullName`: 1–150 chars, trimmed.
  - `workEmail`: `@Email`, stored lowercase.
  - `phone` and `emergencyContactPhone`: "`+` and 7–15 digits; spaces/dashes accepted on input", normalized before save.
  - `jobTitle`: 1–100. `homeAddress`: ≤ 500. `emergencyContactName`: ≤ 150. `emergencyContactRelationship`: ≤ 50. `notes`: ≤ 2,000.
  - `dateOfBirth`: in the past, and age ≥ 14 at `startDate`.
  - `version`: `@Version long`.
  - Relations: `department` (required), `location` (optional), `manager` (optional) as ManyToOne.
  - Derived methods `isPreHire()` and `isTerminated()`.
  - `toString` must exclude personal fields and notes.
- [X] T020 Create `EmployeeRepository` in `backend/src/main/java/com/example/tracker/employee/EmployeeRepository.java`, extending `JpaRepository` and `JpaSpecificationExecutor`, with:
  - `existsByEmployeeNumberIgnoreCase`, `existsByWorkEmailIgnoreCase` and `findByManagerId`.
  - Native recursive-CTE query `boolean isInSubtree(UUID rootId, UUID candidateId)`.
- [X] T021 Create the `UserAccount` entity and `UserAccountRepository` in `backend/src/main/java/com/example/tracker/account/`:
  - Fields: `username` (unique, case-insensitive), `passwordHash`, `role`, optional OneToOne `employee` (unique), `enabled`, `mustChangePassword`, `failedAttempts` (default 0), `lockedUntil`, `passwordChangedAt`, `lastLoginAt`.
  - Repository methods: `findByUsernameIgnoreCase`, `findByEmployeeId`, `countByRoleAndEnabledTrue`.
- [X] T022 Create the `EmployeeChange` entity (append-only, no `updatedAt`; `changeType` in `CREATED`, `UPDATED`, `STATUS_CHANGED`, `REACTIVATED`, `SELF_UPDATED`) and `EmployeeChangeRepository` (paged `findByEmployeeIdOrderByChangedAtDesc`, plus a variant filtered by a set of field names) in `backend/src/main/java/com/example/tracker/history/`
- [X] T023 Create `ChangeRecorder` in `backend/src/main/java/com/example/tracker/history/ChangeRecorder.java`:
  - Method `recordDiff(Employee before, Employee after, ChangeType type, UserAccount actor)` writes one row per changed field, with display strings (foreign keys as display names) and null for empty.
  - Methods `recordCreated(...)` and `recordField(employeeId, fieldName, oldDisplay, newDisplay, type, actor)`.
  - It must be called inside the caller's transaction (`Propagation.MANDATORY`).
- [X] T024 Create `CurrentUser` in `backend/src/main/java/com/example/tracker/auth/CurrentUser.java`: a request-scoped helper that resolves the signed-in `UserAccount` (id, role, employeeId) from the `SecurityContext`, with `isAdmin()` and `isSelf(UUID employeeId)`

### Errors

- [X] T025 [P] Create `ApiException` and the `ErrorCode` enum in `backend/src/main/java/com/example/tracker/common/`. `ErrorCode` lists every code in the contract's `Problem.code` (`DUPLICATE_EMPLOYEE_NUMBER`, `DUPLICATE_EMAIL`, `STALE_VERSION`, `REPORTING_CYCLE`, `INVALID_TRANSITION`, `END_BEFORE_START`, `DIRECT_REPORTS`, `IN_USE`, `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED`, `RESET_TOKEN_INVALID`, `ACCOUNT_EXISTS`, `EMPLOYEE_TERMINATED`, `SELF_LOCKOUT`, `LAST_ADMIN`, `DUPLICATE_EFFECTIVE_DATE`), each with its HTTP status.
- [X] T026 Create `GlobalExceptionHandler` (`@RestControllerAdvice`) in `backend/src/main/java/com/example/tracker/config/GlobalExceptionHandler.java`. It returns RFC 9457 `ProblemDetail` with a `code` property and maps:
  - `ApiException` to its code and status.
  - `MethodArgumentNotValidException` and `ConstraintViolationException` to 400 with `fieldErrors: [{field, message}]`.
  - `ObjectOptimisticLockingFailureException` to 409 `STALE_VERSION`.
  - `DataIntegrityViolationException` on the unique indexes to 409 `DUPLICATE_EMPLOYEE_NUMBER` or `DUPLICATE_EMAIL`.
  - `AccessDeniedException` to 403.
  - Unknown fields on strict DTOs to 400.

### Sign-in and accounts (FR-002, FR-003a–FR-003f)

- [X] T027 [P] Add `backend/src/main/resources/common-passwords.txt` (the 100k most common passwords from SecLists, one per line). Create `PasswordPolicy` in `backend/src/main/java/com/example/tracker/auth/PasswordPolicy.java`. It loads the list into a lowercase `HashSet` at startup, and `validate(String)` rejects length < 12, length > 128, or a case-insensitive match in the list, with field errors.
- [X] T028 Create `SecurityConfig` in `backend/src/main/java/com/example/tracker/config/SecurityConfig.java`:
  - `BCryptPasswordEncoder(12)`.
  - `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `CsrfTokenRequestAttributeHandler`.
  - Spring Session JDBC with `FindByIndexNameSessionRepository` and `SpringSessionBackedSessionRegistry`.
  - `permitAll` for `POST /api/v1/auth/login`, `/api/v1/auth/password-reset` and `/api/v1/auth/password-reset/confirm`, plus static frontend assets. Everything else under `/api/**` is authenticated.
  - `hasRole('ADMIN')` for the admin-only paths in the contract: POST, PATCH and PUT on `/employees`, `/employees/*/status`, `/employees/*/reactivate`, `/employees/*/sensitive/**`, `/employees/*/compensation`, `/sensitive-access-log`, `/reports/**`, `/employees/export`, `/users/**`, and POST, PUT and DELETE on `/departments/**` and `/locations/**`.
  - JSON 401/403 entry points returning ProblemDetail.
  - Form login and HTTP basic disabled.
- [X] T029 Create `LoginService` in `backend/src/main/java/com/example/tracker/auth/LoginService.java`:
  - Looks up the user case-insensitively and rejects if `enabled=false`.
  - If `lockedUntil > now`, returns 401 with code `ACCOUNT_LOCKED`, but with the same generic message.
  - Verifies the BCrypt hash. On failure it increments `failedAttempts`, and on the 5th consecutive failure sets `lockedUntil = now + 15 min`.
  - On success it resets `failedAttempts`, sets `lastLoginAt`, and stores the authentication in a new session (session fixation protection).
  - For an unknown username it still runs a dummy BCrypt check (timing parity), with the identical message "Invalid username or password".
- [X] T030 Create `AuthController` in `backend/src/main/java/com/example/tracker/auth/AuthController.java`: `POST /api/v1/auth/login` (returns `CurrentUser` DTO `{id, username, role, employeeId, mustChangePassword}`), `POST /api/v1/auth/logout` (204, invalidates the session), `GET /api/v1/auth/me`
- [X] T031 Create `SessionRevoker` in `backend/src/main/java/com/example/tracker/account/SessionRevoker.java`. Method `revokeAll(String username, Optional<String> exceptSessionId)` deletes the user's sessions via `FindByIndexNameSessionRepository.findByPrincipalName`.
- [X] T032 Create `MustChangePasswordFilter` in `backend/src/main/java/com/example/tracker/auth/MustChangePasswordFilter.java` and register it after authentication in `SecurityConfig`. While the user has `mustChangePassword=true`, it allows only `/api/v1/auth/me`, `/api/v1/auth/change-password` and `/api/v1/auth/logout`, and returns 403 ProblemDetail `PASSWORD_CHANGE_REQUIRED` for everything else.
- [X] T033 Add `POST /api/v1/auth/change-password` to `AuthController`, with the logic in `backend/src/main/java/com/example/tracker/auth/PasswordService.java`:
  - Verify `currentPassword` (401 if wrong) and validate `newPassword` with `PasswordPolicy`.
  - Set the hash, `mustChangePassword=false` and `passwordChangedAt`.
  - Call `SessionRevoker.revokeAll(username, currentSessionId)`. Return 204.
- [X] T034 Create the `PasswordResetToken` entity and repository, and `PasswordResetService`, in `backend/src/main/java/com/example/tracker/account/`.
  - `request(username)` always completes normally. Only for an existing, enabled account it:
    - invalidates earlier unused tokens;
    - generates 32 random bytes as a base64url token;
    - stores the SHA-256 hex hash with `expiresAt = now + 1h`;
    - emails `${tracker.app-base-url}/reset-password?token=...` via `JavaMailSender`, from `tracker.mail.from`.
  - `confirm(token, newPassword)` finds the token by hash. If it's missing, expired or used, it throws 400 `RESET_TOKEN_INVALID`. Otherwise it validates the policy, sets the password, clears `failedAttempts`, `lockedUntil` and `mustChangePassword`, sets `usedAt`, and revokes all sessions.
- [X] T035 Create `PasswordResetController` in `backend/src/main/java/com/example/tracker/auth/PasswordResetController.java`: `POST /api/v1/auth/password-reset` (always 202) and `POST /api/v1/auth/password-reset/confirm` (204)
- [X] T036 Create `AccountService` and `AccountController` in `backend/src/main/java/com/example/tracker/account/`, implementing `/api/v1/users` per the contract:
  - **List** with `q`, `role` and `enabled` filters.
  - **Create** from `{employeeId, role, temporaryPassword}`. The username is the employee's work email, and `mustChangePassword=true`. Returns 409 `ACCOUNT_EXISTS` if the employee already has an account, and 409 `EMPLOYEE_TERMINATED` if the employee is terminated.
  - **PATCH** of role or enabled. Disabling revokes sessions. Returns 409 `SELF_LOCKOUT` when admins target themselves, and 409 `LAST_ADMIN` when it would leave zero enabled admins.
  - **POST `/users/{id}/temporary-password`** validates the policy, sets `mustChangePassword=true`, clears the lockout, revokes sessions and returns 204.
  - The `UserAccount` DTO includes `locked = lockedUntil > now`.
- [X] T037 Create `BootstrapAdminRunner` in `backend/src/main/java/com/example/tracker/account/BootstrapAdminRunner.java`. On startup, if no enabled `ADMIN` exists and the env vars `TRACKER_BOOTSTRAP_ADMIN_EMAIL` and `TRACKER_BOOTSTRAP_ADMIN_PASSWORD` are set, it creates an `ADMIN` account (no employee link) with `mustChangePassword=true` and logs a warning to remove the vars.
- [X] T038 Create `DevSeedRunner` in `backend/src/main/java/com/example/tracker/seed/DevSeedRunner.java` (profile `dev`, idempotent: it skips if any employee exists). It seeds:
  - 4 departments (Engineering, Sales, Finance, People) and 3 locations (Head Office, Branch Office, Remote).
  - About 60 employees with a 3-level reporting chain, including an Engineering manager with direct and indirect reports, 3 `ON_LEAVE`, 4 `TERMINATED` and 1 pre-hire.
  - Accounts, all with the password `ChangeMe-12345`:
    - `admin@example.com` (ADMIN);
    - `alice@example.com` (EMPLOYEE, an active engineer);
    - `newhire@example.com` (EMPLOYEE, with `mustChangePassword=true`);
    - `former@example.com` (EMPLOYEE, linked to a terminated employee, `enabled=false`).

### Frontend foundation

- [X] T039 [P] Create `frontend/src/api/client.ts`: a `fetch` wrapper with `credentials: "same-origin"`. It reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` on mutating requests, parses `application/problem+json` into a typed `ApiError {status, code, detail, fieldErrors}`, and on 401 dispatches a `session-expired` event. It also exports typed helpers based on `frontend/src/api/schema.d.ts`.
- [X] T040 [P] Create `frontend/src/auth/AuthContext.tsx`: it loads `/auth/me` and exposes `user`, `isAdmin`, `signIn`, `signOut` and `refresh`, and redirects to `/sign-in` on `session-expired`. Also create `frontend/src/auth/guards.tsx` with `RequireAuth` (redirects to `/change-password` when `mustChangePassword`) and `RequireAdmin`.
- [X] T041 [P] Create `frontend/src/auth/SignInPage.tsx` (email and password, the generic error message, a "Forgot password?" link) and `frontend/src/auth/ChangePasswordPage.tsx` (current, new and confirm fields; client-side minimum of 12 characters; shows server field errors)
- [X] T042 [P] Create `frontend/src/auth/ForgotPasswordPage.tsx` (always shows "If an account exists, we've emailed a reset link") and `frontend/src/auth/ResetPasswordPage.tsx` (reads `?token=`, shows the new and confirm fields, and maps `RESET_TOKEN_INVALID` to "This link is invalid or has expired")
- [X] T043 Create `frontend/src/App.tsx` and `frontend/src/layout/AppShell.tsx` with a Mantine provider, a `QueryClient`, and routes for the auth pages plus guarded placeholders for the story pages. The navigation shows Directory and My profile to everyone, and Overview, Departments, Locations, Users and Access log only to `isAdmin`.
- [X] T044 Create `frontend/src/pages/UsersPage.tsx` (admin): list accounts with role, enabled and locked status, create a sign-in for an employee (employee picker, role, temporary password), change role, enable or disable, and issue a temporary password. Show the `SELF_LOCKOUT`, `LAST_ADMIN`, `ACCOUNT_EXISTS` and `EMPLOYEE_TERMINATED` messages.

### Test harness and foundation tests

- [X] T045 [P] Create `backend/src/test/java/com/example/tracker/support/AbstractIntegrationTest.java`: `@SpringBootTest` + `@AutoConfigureMockMvc`, a shared Testcontainers `postgres:16` and `axllent/mailpit` container with `@ServiceConnection`/`@DynamicPropertySource`, a fixed test data key, and helpers `loginAs(role)`, `newEmployee(...)` and `csrf()`
- [X] T046 [P] Create `backend/src/test/java/com/example/tracker/support/ContractAssert.java`: it loads `specs/001-employee-tracker/contracts/openapi.yaml` with `OpenApiInteractionValidator` and exposes a MockMvc `ResultMatcher` that fails when a response doesn't match the contract
- [X] T047 Create `backend/src/test/java/com/example/tracker/security/AuthSecurityIT.java`. It covers:
  - The same 401 message for an unknown user and a wrong password.
  - Lockout on the 5th failure, and still refused within 15 minutes using a fixed `Clock`.
  - The password policy rejecting 11 characters and `Password1234`.
  - `PASSWORD_CHANGE_REQUIRED` for a must-change user on `/employees`.
  - Change-password revoking other sessions.
  - The reset token being single-use, expiring after 1 hour, and not emailing unknown usernames (checked against the Mailpit API).
  - A disabled account being unable to sign in.
  - An idle session expiring after 30 minutes.
  - Contract conformance of the `/auth/*` responses via `ContractAssert`.

**Checkpoint**: Users can sign in, change and reset passwords, and admins can manage accounts. Story work can start.

---

## Phase 3: User Story 1 - Add and maintain employee records (Priority: P1) 🎯 MVP

**Goal**: An admin can create, view and edit employee records, manage departments and locations, see per-field change history, and record sensitive data (compensation, government ID, tax ID, bank details), which is encrypted, masked, revealable, and logged on every access.

**Independent Test**: Sign in as admin, add an employee, see every value on the profile, edit the job title, and see `jobTitle: old → new` in the history. Enter bank details: the profile shows `••••1234`, and Reveal shows the full number and adds a `REVEAL` row in the access log (quickstart US1 steps 1–6).

### Tests for User Story 1 ⚠️ (write first and confirm they fail)

- [X] T048 [P] [US1] Create `backend/src/test/java/com/example/tracker/contract/EmployeeAdminContractIT.java`. It checks responses with `ContractAssert` for `POST /employees`, `GET /employees/{id}` (admin view), `PATCH /employees/{id}`, `GET /employees/{id}/history`, the `/departments` and `/locations` CRUD, and the 400/409/422 problem shapes.
- [X] T049 [P] [US1] Create `backend/src/test/java/com/example/tracker/integration/EmployeeRecordsIT.java`. It covers:
  - Create success, with a `CREATED` history row.
  - Required-field and invalid-email errors per field.
  - A duplicate employee number giving 409 `DUPLICATE_EMPLOYEE_NUMBER` (case-insensitive).
  - A duplicate email giving 409 `DUPLICATE_EMAIL`.
  - A job-title edit producing exactly one `UPDATED` row with old and new values, the actor and a timestamp (SC-004).
  - A stale `version` giving 409 `STALE_VERSION`.
  - Self as manager, and a manager in the employee's own subtree, both giving 422 `REPORTING_CYCLE`.
  - A future start date being accepted, with `isPreHire=true`.
  - An email change updating the linked `UserAccount.username`.
  - Deleting a department in use giving 409 `IN_USE`.
  - No delete endpoint for employees (FR-011).
- [X] T050 [P] [US1] Create `backend/src/test/java/com/example/tracker/integration/SensitiveDataIT.java`. It covers:
  - `PUT /employees/{id}/sensitive`, after which the raw `employee_sensitive` columns read via `JdbcTemplate` don't contain the plain values (FR-006f).
  - `GET` returning the `••••` + last-4 form.
  - Reveal returning the full value, with `Cache-Control: no-store`.
  - Each reveal and each changed field adding exactly one `sensitive_access_log` row (SC-008).
  - `employee_change` rows for sensitive fields holding masked values only.
  - Compensation: POST creates a record, GET writes a `REVEAL` of `compensation`, and a duplicate effective date gives 409 `DUPLICATE_EFFECTIVE_DATE`.
  - `current` being the latest effective date ≤ today.
  - An `EMPLOYEE` getting 403 on every `/sensitive`, `/compensation` and `/sensitive-access-log` endpoint.
  - A wrong data key making reveal fail with 500 ProblemDetail, while the masked GET still works.

### Implementation for User Story 1: reference data and records

- [X] T051 [P] [US1] Create `ReferenceDataService`, `DepartmentController` and `LocationController` in `backend/src/main/java/com/example/tracker/reference/`, implementing `GET`, `POST`, `PUT /{id}` and `DELETE /{id}` for `/api/v1/departments` and `/api/v1/locations`:
  - Delete is blocked while any employee, of any status, references the record (409 `IN_USE`, with `employeeCount` in `detail`).
  - The department head must not be terminated.
  - `employeeCount` is included only for admins.
- [X] T052 [P] [US1] Create the request DTOs `EmployeeCreateRequest` and `EmployeeUpdateRequest` (JsonNullable/Optional semantics so an explicit `null` clears a field) and the response DTO `AdminEmployeeView`, matching the contract's `EmployeeDetail` with `view=ADMIN`, in `backend/src/main/java/com/example/tracker/employee/dto/`. `status` on create accepts only `ACTIVE` or `ON_LEAVE`.
- [X] T053 [US1] Create `EmployeeService` in `backend/src/main/java/com/example/tracker/employee/EmployeeService.java`:
  - **`create`**: validate uniqueness of `employeeNumber` and `workEmail` (case-insensitive), resolve department, location and manager (the manager must not be `TERMINATED`), save, and call `ChangeRecorder.recordCreated`.
  - **`update(id, request)`**:
    - Check `version` and reject a manager that is the employee themselves or is in their subtree (`EmployeeRepository.isInSubtree`), giving 422 `REPORTING_CYCLE`.
    - Apply only the fields present in the request and call `ChangeRecorder.recordDiff(..., UPDATED, actor)`.
    - If `workEmail` changed, update the linked `UserAccount.username`.
  - **`get(id)`**.
  - Everything is `@Transactional`, and the service has no delete method.
- [X] T054 [US1] Create `EmployeeViewPolicy` in `backend/src/main/java/com/example/tracker/employee/EmployeeViewPolicy.java`. In this story it only implements `toAdminView(Employee)`, which fills `directReports` from `findByManagerId`. Make it the only class that builds employee response DTOs; US2 adds the directory and self views.
- [X] T055 [US1] Create `EmployeeController` in `backend/src/main/java/com/example/tracker/employee/EmployeeController.java`:
  - `POST /api/v1/employees` returns 201 with a `Location` header.
  - `GET /api/v1/employees/{id}` and `PATCH /api/v1/employees/{id}` (admin view for now).
  - `GET /api/v1/employees/{id}/history` (admin, paged, default size 50).

### Implementation for User Story 1: sensitive data

- [X] T056 [US1] Create `backend/src/main/resources/db/migration/V3__sensitive.sql` with:
  - Table `employee_sensitive`: unique `employee_id`, text `gov_id_type`, bytea columns `gov_id_number`, `tax_id`, `bank_account_holder`, `bank_account_number` and `bank_routing_code`, text columns `gov_id_last4`, `tax_id_last4`, `bank_account_last4` and `bank_routing_last4`, text `bank_name`, text `key_id`, and bigint `version`.
  - Table `compensation_record`: bytea `amount`, `currency char(3)`, `pay_frequency` checked in `('ANNUAL','MONTHLY','HOURLY')`, `effective_date`, `reason varchar(200)`, `created_by` FK, and `unique(employee_id, effective_date)`.
  - Table `sensitive_access_log`: `actor_id`, `employee_id`, `field`, `action` checked in `('REVEAL','CREATE','UPDATE')`, `occurred_at`, with indexes `(employee_id, occurred_at desc)` and `(actor_id, occurred_at desc)`.
- [X] T057 [P] [US1] Create `DataKeyProvider` in `backend/src/main/java/com/example/tracker/sensitive/crypto/DataKeyProvider.java`. It reads the active key id from `tracker.data-key.active-id`, and each key from the env var `TRACKER_DATA_KEY_<id>` as base64 of 32 bytes. It fails startup if the active key is missing or isn't 32 bytes, and exposes `activeKeyId()` and `key(String id)`.
- [X] T058 [US1] Create `FieldCipher` and `EncryptedStringConverter` (a JPA `AttributeConverter<String, byte[]>`) in `backend/src/main/java/com/example/tracker/sensitive/crypto/`:
  - Algorithm: AES-256-GCM with a random 12-byte IV and a 128-bit tag.
  - Stored format: `[1 byte format version][1 byte key-id length][key-id bytes][12-byte IV][ciphertext+tag]`.
  - Decryption uses the key id embedded in the value, so keys can be rotated.
  - A failure throws `SensitiveDecryptionException`, which never logs the value.
- [X] T059 [P] [US1] Create the `EmployeeSensitive` entity in `backend/src/main/java/com/example/tracker/sensitive/EmployeeSensitive.java`:
  - Encrypted fields: `govIdNumber` "4–30 chars, `[A-Za-z0-9-]`", `taxId` "4–30 chars", `bankAccountHolder` "≤ 150 chars", `bankAccountNumber` "4–34 chars (covers IBAN)" and `bankRoutingCode` "≤ 20 chars".
  - Plain fields: `govIdType` ("Required if `gov_id_number` is set; free text ≤ 50"), `bankName` (≤ 150) and the `*Last4` columns, set automatically from the plain value.
  - `@Version` and `toString` without values.
  - Add its repository.
- [X] T060 [P] [US1] Create the `CompensationRecord` entity and repository in `backend/src/main/java/com/example/tracker/sensitive/`:
  - `amount`: an encrypted string holding a decimal "> 0, with up to 2 decimals".
  - `currency`: ISO 4217, 3 uppercase letters. `payFrequency`: `ANNUAL`, `MONTHLY` or `HOURLY`. `reason`: ≤ 200.
  - Also `effectiveDate` and `createdBy`. The entity has no update path.
- [X] T061 [P] [US1] Create the `SensitiveAccessLog` entity (append-only), its repository (paged filtering by employeeId, actorId and from/to) and `SensitiveAccessLogger.log(actor, employeeId, field, action)` (`Propagation.MANDATORY`) in `backend/src/main/java/com/example/tracker/sensitive/`
- [X] T062 [P] [US1] Create `Masking` in `backend/src/main/java/com/example/tracker/sensitive/Masking.java`. `mask(last4)` returns `"••••" + last4`, or null if not set. `maskHolder(value)` returns `"••••"` when set.
- [X] T063 [US1] Create `SensitiveDataService` in `backend/src/main/java/com/example/tracker/sensitive/SensitiveDataService.java`:
  - **`getMasked(employeeId)`**: returns masked values from the last-4 columns and never decrypts.
  - **`replace(employeeId, SensitiveInput)`**: full replacement with a version check. For each changed field it:
    - writes the access log with `CREATE` (previously null) or `UPDATE`;
    - writes `ChangeRecorder.recordField` with the **masked** old and new values.
  - **`reveal(employeeId, field)`**: decrypts one field and logs `REVEAL`.
  - **`listCompensation(employeeId)`**: decrypts the amounts, logs `REVEAL` of `compensation`, and computes `current` as the latest `effectiveDate` ≤ today.
  - **`addCompensation`**: 409 `DUPLICATE_EFFECTIVE_DATE` for an existing date. Logs `CREATE` of `compensation` and records a history row reading `compensation: (changed)`, with no amount.
  - Everything is `@Transactional`.
- [X] T064 [US1] Create `SensitiveDataController` in `backend/src/main/java/com/example/tracker/sensitive/SensitiveDataController.java` with `GET`/`PUT /api/v1/employees/{id}/sensitive`, `POST /api/v1/employees/{id}/sensitive/reveal`, and `GET`/`POST /api/v1/employees/{id}/compensation`. Reveal and the compensation GET set `Cache-Control: no-store`. Also create `SensitiveAccessLogController` (`GET /api/v1/sensitive-access-log`).
- [X] T065 [US1] Extend `DevSeedRunner` in `backend/src/main/java/com/example/tracker/seed/DevSeedRunner.java` to add sensitive data and 2–3 compensation records for 5 seeded employees, including Alice

### Frontend for User Story 1

- [X] T066 [P] [US1] Create `frontend/src/lib/schemas.ts`: Zod schemas mirroring the server rules from data-model.md for employee create and update (same patterns and lengths, date of birth ≥ 14 years before the start date), sensitive input and compensation input (`^\d{1,12}(\.\d{1,2})?$`, `^[A-Z]{3}$`)
- [X] T067 [P] [US1] Create `frontend/src/pages/DepartmentsPage.tsx` and `frontend/src/pages/LocationsPage.tsx` (admin): list with employee counts, add, rename, and delete with the `IN_USE` message showing the count
- [X] T068 [US1] Create `frontend/src/pages/EmployeeFormPage.tsx` (routes `/employees/new` and `/employees/:id/edit`):
  - Fields are grouped as Identity, Job, Personal and Notes, using React Hook Form + Zod.
  - Manager is a searchable picker.
  - Server `fieldErrors` are mapped onto the fields. `STALE_VERSION` shows "This record was changed by someone else" with a Reload action.
  - Includes a "Save and add another" button.
- [X] T069 [P] [US1] Create `frontend/src/components/HistoryList.tsx`: a paged list of `ChangeEntry` showing field, old → new, who and when
- [X] T070 [P] [US1] Create `frontend/src/components/SensitivePanel.tsx` (admin): shows masked values, with a Reveal button per field that calls reveal and shows the value until navigation (never cached; `gcTime: 0`). Its edit form uses `SensitiveInput`.
- [X] T071 [P] [US1] Create `frontend/src/components/CompensationTable.tsx` (admin): a collapsed "Show compensation" button that fetches and lists records (newest first, current one highlighted), plus an add-record form
- [X] T072 [US1] Create `frontend/src/pages/EmployeeProfilePage.tsx` (route `/employees/:id`), rendering the admin view: all fields, manager and direct-report links, `HistoryList`, `SensitivePanel` and `CompensationTable`, plus an Edit button
- [X] T073 [P] [US1] Create `frontend/src/pages/AccessLogPage.tsx` (admin): a paged table of the sensitive access log with filters for employee, actor and date range

**Checkpoint**: US1 works on its own. Run quickstart US1 steps 1–6.

---

## Phase 4: User Story 2 - Find and view employees, with self-service (Priority: P2)

**Goal**: Any signed-in user can search, filter and sort a company directory and open profiles, seeing only the fields their role allows. Employees can update their own phone and emergency contact.

**Independent Test**: As Alice, search a name and filter by department. Results show only active and on-leave colleagues, a colleague's profile shows only directory fields, a terminated employee's URL gives "not found", and editing her own phone produces a `SELF_UPDATED` history row (quickstart US2 steps 1–5).

### Tests for User Story 2 ⚠️

- [X] T074 [P] [US2] Create `backend/src/test/java/com/example/tracker/security/VisibilityMatrixIT.java` (SC-009), a parameterized test over role × target × endpoint:
  - **Roles**: ADMIN, EMPLOYEE.
  - **Targets**: the caller themselves, an active colleague, a terminated employee.
  - **Endpoints**: `GET /employees`, `GET /employees/{id}`, `GET /employees/{id}/history`, `GET /me/profile`, and, where allowed, `/employees/export`.
  - **Assertions**:
    - an EMPLOYEE response never contains the keys `dateOfBirth`, `homeAddress`, `emergencyContact`, `notes` or `endDate`, nor any seeded sensitive value or last-4 string, except the employee's own personal fields in the SELF view;
    - an EMPLOYEE never sees `notes` or sensitive data, even their own;
    - a terminated target gives 404 for an EMPLOYEE;
    - every response passes `ContractAssert`.
- [X] T075 [P] [US2] Create `backend/src/test/java/com/example/tracker/integration/DirectorySearchIT.java`. It covers:
  - Partial, case-insensitive `q` on the name and email.
  - `q` matching the employee number only for ADMIN.
  - Combined department + location + status filters, with the correct `totalItems`.
  - The EMPLOYEE `status=TERMINATED` and `includeTerminated=true` being ignored or rejected as the contract says.
  - Each sort option, with `startDate` and `status` sorts giving 400 for EMPLOYEE.
  - Paging defaults (size 25, maximum 100).
  - An empty result returning `items: []`.
- [X] T076 [P] [US2] Create `backend/src/test/java/com/example/tracker/integration/SelfServiceIT.java`. It covers:
  - `PATCH /me/profile` with the phone and emergency contact succeeding and writing `SELF_UPDATED` rows.
  - Any other field, for example `jobTitle`, giving 400.
  - A stale version giving 409.
  - An admin account with no employee link getting 404 on `/me/profile`.
  - An EMPLOYEE getting 403 on `PATCH /employees/{id}` for their own id.
  - `GET /employees/{ownId}/history` showing only self-view fields, while another employee's history gives 404.

### Implementation for User Story 2

- [X] T077 [P] [US2] Create the response DTOs `DirectoryEmployeeView` (fields: `id`, `view=DIRECTORY`, `fullName`, `workEmail`, `phone`, `jobTitle`, `department`, `location`, `manager`, `directReports` of non-terminated employees only) and `SelfEmployeeView` (the directory fields plus `employeeNumber`, `employmentType`, `status`, `startDate`, `dateOfBirth`, `homeAddress`, `emergencyContact`, `version`, with `view=SELF`) in `backend/src/main/java/com/example/tracker/employee/dto/`. Also create a list-item DTO for each view, matching the contract's `EmployeeSummary`.
- [X] T078 [US2] Extend `EmployeeViewPolicy` in `backend/src/main/java/com/example/tracker/employee/EmployeeViewPolicy.java`:
  - **`viewFor(CurrentUser, Employee)`**: ADMIN gets the admin view, the caller's own record gets the self view, an active or on-leave employee gets the directory view, and anything else throws not-found.
  - **`summaryFor(...)`** for list items.
  - **`visibleHistoryFields(CurrentUser, Employee)`**: all fields for ADMIN, the self-view field names for self, and access denied (404) otherwise.
- [X] T079 [US2] Create `EmployeeSpecifications` in `backend/src/main/java/com/example/tracker/employee/EmployeeSpecifications.java` with:
  - `q`: `ILIKE %term%` on `full_name` and `work_email`, plus `employee_number` only when admin. Escape `%` and `_` in the term.
  - `departmentIn`, `locationIn`, `managerEq`, `statusIn` and `employmentTypeIn` (admin only).
  - Default status: `ACTIVE` and `ON_LEAVE` unless an admin passes `includeTerminated=true` or an explicit `status`. For EMPLOYEE, the status filter is always intersected with `{ACTIVE, ON_LEAVE}`.
- [X] T080 [US2] Add `GET /api/v1/employees` to `EmployeeController` in `backend/src/main/java/com/example/tracker/employee/EmployeeController.java`:
  - Parameters per the contract. Sort values map to `fullName`, `department.name`, `startDate` and `status`, and `startDate`/`status` sorts give 400 for EMPLOYEE.
  - `page` defaults to 0 and `size` to 25 (maximum 100).
  - Returns `EmployeePage` built with `summaryFor`.
  - Also switch `GET /employees/{id}` to `viewFor`, and `GET /employees/{id}/history` to `visibleHistoryFields`, opening the history route to EMPLOYEE for their own record.
- [X] T081 [US2] Create `SelfServiceController` in `backend/src/main/java/com/example/tracker/employee/SelfServiceController.java` with `GET /api/v1/me/profile` and `PATCH /api/v1/me/profile`:
  - The request DTO `SelfUpdateRequest` has `@JsonIgnoreProperties(ignoreUnknown = false)` and holds only `version`, `phone` and `emergencyContact`. Unknown fields give 400.
  - Add `EmployeeService.updateSelf`, which writes `ChangeRecorder` rows with type `SELF_UPDATED`.
  - An account with no linked employee gets 404.

### Frontend for User Story 2

- [X] T082 [P] [US2] Create `frontend/src/components/FilterBar.tsx`:
  - A debounced (300 ms) search box, department, location and manager selects, and for admins only, status and employment-type multi-selects plus an "Include former employees" switch.
  - The state is kept in the URL search params.
  - A "Clear filters" action.
- [X] T083 [P] [US2] Create `frontend/src/components/EmployeeTable.tsx`: sortable columns (Name and Department for everyone; Start date and Status for admins), a pager, rows linking to `/employees/:id`, a pre-hire badge "Starting on {date}", and the empty state "No employees found" with a Clear filters button
- [X] T084 [US2] Create `frontend/src/pages/DirectoryPage.tsx` (route `/`): it combines `FilterBar` and `EmployeeTable` via TanStack Query on `GET /employees` and shows the result count. Admins also see an "Add employee" button.
- [X] T085 [US2] Update `frontend/src/pages/EmployeeProfilePage.tsx` to render by `view`. In the DIRECTORY view it shows only the directory fields, with no Edit, sensitive panel, compensation or history. In the SELF view it shows a link to My profile. A 404 shows "Employee not found".
- [X] T086 [US2] Create `frontend/src/pages/MyProfilePage.tsx` (route `/me`): the self view is read-only except for an editable Phone and Emergency contact form, which sends PATCH `/me/profile` with `version` and handles `STALE_VERSION`. Below it, `HistoryList` shows the user's own history.

**Checkpoint**: US1 and US2 both work. Run quickstart US2 steps 1–5 and the sign-in scenarios.

---

## Phase 5: User Story 3 - Track employment status changes and offboarding (Priority: P3)

**Goal**: Admins move employees between Active, On Leave and Terminated, are warned about direct reports when terminating, and can reactivate rehires. Termination immediately ends the employee's sign-in.

**Independent Test**: Change an employee to On Leave and back; terminate them with an end date while they're signed in elsewhere (their session ends, and they're hidden from the directory); reactivate them (they must change their password at sign-in) (quickstart US3 steps 1–5).

### Tests for User Story 3 ⚠️

- [X] T087 [P] [US3] Create `backend/src/test/java/com/example/tracker/integration/LifecycleIT.java`. It covers:
  - `ACTIVE`→`ON_LEAVE`→`ACTIVE` writing `STATUS_CHANGED` rows.
  - A no-op transition giving 422 `INVALID_TRANSITION`.
  - `TERMINATED` without an end date giving 400.
  - An end date before the start date giving 422 `END_BEFORE_START`.
  - Terminating a manager with reports and no acknowledgement giving 409 `DIRECT_REPORTS`, listing the reports. With `acknowledgeDirectReports=true` it succeeds and the reports keep their `manager_id`.
  - Termination setting `UserAccount.enabled=false` and deleting that user's `spring_session` rows, so an existing MockMvc session gets 401.
  - Reactivate from a non-terminated status giving 422.
  - Reactivate with `startDate < endDate` giving 422.
  - Reactivate clearing `endDate`, writing `REACTIVATED`, and re-enabling the account with `mustChangePassword=true`.
  - Contract conformance for `/status` and `/reactivate`.

### Implementation for User Story 3

- [X] T088 [US3] Create `LifecycleService` in `backend/src/main/java/com/example/tracker/lifecycle/LifecycleService.java`:
  - **`changeStatus(id, StatusChange)`**:
    - Check the version. Allowed transitions are `ACTIVE`↔`ON_LEAVE`, `ACTIVE`→`TERMINATED` and `ON_LEAVE`→`TERMINATED`; the same status gives 422 `INVALID_TRANSITION`.
    - `TERMINATED` requires `endDate` (400 if missing) and `endDate ≥ startDate` (422 `END_BEFORE_START`).
    - If the employee has direct reports and `acknowledgeDirectReports` isn't true, throw 409 `DIRECT_REPORTS` with the `directReports` refs.
    - On termination, disable the linked `UserAccount` and call `SessionRevoker.revokeAll`.
    - Record `STATUS_CHANGED` (and the `endDate` field).
  - **`reactivate(id, request)`**:
    - Only from `TERMINATED` (otherwise 422 `INVALID_TRANSITION`), and only with `startDate ≥ previous endDate` (otherwise 422).
    - Set `ACTIVE`, clear `endDate`, apply the optional `jobTitle`, `departmentId` and `managerId` (with the cycle check), and record `REACTIVATED` plus the field diffs.
    - Re-enable the linked account with `mustChangePassword=true`.
  - Everything is `@Transactional`.
- [X] T089 [US3] Create `LifecycleController` in `backend/src/main/java/com/example/tracker/lifecycle/LifecycleController.java` with `POST /api/v1/employees/{id}/status` and `POST /api/v1/employees/{id}/reactivate`, returning the admin view. The `DIRECT_REPORTS` problem includes `directReports: [{id, name}]`.
- [X] T090 [P] [US3] Create `frontend/src/components/StatusDialog.tsx` (admin):
  - Choose the new status. Terminated requires an end date ≥ the start date.
  - On a `DIRECT_REPORTS` 409, show the listed reports with links and a "Terminate anyway" confirmation that resends the request with `acknowledgeDirectReports=true`.
- [X] T091 [P] [US3] Create `frontend/src/components/ReactivateDialog.tsx` (admin): a new start date (≥ the previous end date) and optional job title, department and manager
- [X] T092 [US3] Wire `StatusDialog` and `ReactivateDialog` into `frontend/src/pages/EmployeeProfilePage.tsx` (admin view: "Change status" button, and "Reactivate" shown only when Terminated, with the status badge and end date shown)

**Checkpoint**: US1–US3 work. Run quickstart US3 steps 1–5.

---

## Phase 6: User Story 4 - Headcount overview and export (Priority: P4)

**Goal**: Admins see headcount totals by department, status and employment type, and export the filtered list as CSV.

**Independent Test**: As admin, the Overview totals match the list filtered the same way. Exporting a department filter downloads a CSV with exactly those rows and no sensitive columns. As Alice, both endpoints return 403 (quickstart US4 steps 1–3).

### Tests for User Story 4 ⚠️

- [X] T093 [P] [US4] Create `backend/src/test/java/com/example/tracker/integration/ReportsIT.java`. It covers:
  - Headcount `totalActive` equalling `ACTIVE`+`ON_LEAVE`, with breakdowns matching `GET /employees` counts for the same filters (SC-005).
  - The export rows equalling the filtered list, and the header containing no sensitive column names.
  - A UTF-8 BOM and a non-ASCII name round-tripping.
  - A cell starting with `=`, `+`, `-` or `@` being prefixed with `'`.
  - The `Content-Disposition` filename `employees-YYYY-MM-DD.csv`.
  - EMPLOYEE getting 403 on both endpoints.

### Implementation for User Story 4

- [X] T094 [P] [US4] Create `HeadcountService` and `ReportController` in `backend/src/main/java/com/example/tracker/report/`, implementing `GET /api/v1/reports/headcount` with grouped count queries: `totalActive` is `ACTIVE`+`ON_LEAVE`, `byDepartment` uses active and on-leave employees, and `byStatus` covers all three statuses.
- [X] T095 [P] [US4] Create `CsvWriter` in `backend/src/main/java/com/example/tracker/report/CsvWriter.java`: RFC 4180 quoting, `\r\n` line endings, a UTF-8 BOM, and formula-injection escaping (prefix `'` when a cell starts with `=`, `+`, `-` or `@`)
- [X] T096 [US4] Create `ExportController` in `backend/src/main/java/com/example/tracker/report/ExportController.java`:
  - `GET /api/v1/employees/export` reuses `EmployeeSpecifications` with the list parameters and streams rows via `StreamingResponseBody` in batches of 500.
  - Columns: employee number, full name, work email, phone, job title, department, location, manager, employment type, status, start date, end date, date of birth, home address, emergency contact name, relationship and phone, and notes. It never includes sensitive data.
  - Content type `text/csv`, with `Content-Disposition: attachment; filename="employees-<today>.csv"`.
- [X] T097 [P] [US4] Create `frontend/src/pages/OverviewPage.tsx` (admin): a total-active stat and per-department, per-status and per-type count tables. Each count links to the Directory pre-filtered to that value.
- [X] T098 [US4] Add an "Export CSV" button (admin only) to `frontend/src/pages/DirectoryPage.tsx` that downloads `/api/v1/employees/export` with the current URL filters

**Checkpoint**: All stories work independently.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T099 [P] Add `PerfSeedRunner` in `backend/src/main/java/com/example/tracker/seed/PerfSeedRunner.java` (profile `perf`) that seeds 5,000 employees across 20 departments, and a test `backend/src/test/java/com/example/tracker/integration/SearchPerformanceIT.java` asserting that `GET /employees?q=...&departmentId=...` responds in < 1 s at 5,000 rows (SC-003)
- [X] T100 [P] Add security headers in `backend/src/main/java/com/example/tracker/config/SecurityConfig.java`:
  - `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`.
  - HSTS outside the `dev` profile.
- [X] T101 [P] Add `backend/src/main/resources/logback-spring.xml` with JSON-style console logging. Add a test `backend/src/test/java/com/example/tracker/security/NoSensitiveLoggingIT.java` that captures logs during the `SensitiveDataIT` flows and asserts that no seeded sensitive value or password appears.
- [X] T102 [P] Add `MailHealthCheck` in `backend/src/main/java/com/example/tracker/config/MailHealthCheck.java`, which tests the SMTP connection at startup outside `dev`. Failure logs an error but doesn't stop startup, because admins can still issue temporary passwords.
- [X] T103 [P] Create the Playwright config `frontend/playwright.config.ts` (baseURL `http://localhost:5173`) and end-to-end specs, one per story, following the quickstart scenarios:
  - `frontend/tests/e2e/auth.spec.ts`
  - `frontend/tests/e2e/us1-records.spec.ts`
  - `frontend/tests/e2e/us2-directory.spec.ts`
  - `frontend/tests/e2e/us3-lifecycle.spec.ts`
  - `frontend/tests/e2e/us4-reports.spec.ts`
- [X] T104 [P] Add component tests `frontend/tests/unit/SensitivePanel.test.tsx` (masked by default; Reveal shows the value; the value isn't kept after unmount) and `frontend/tests/unit/FilterBar.test.tsx` (URL sync; admin-only controls hidden for EMPLOYEE)
- [X] T105 [P] Write `README.md` at the repository root covering:
  - Prerequisites (JDK 21, Docker) and running in dev (link to `specs/001-employee-tracker/quickstart.md`).
  - Production env vars: `DB_*`, `TRACKER_DATA_KEY_ID`, `TRACKER_DATA_KEY_<id>`, `TRACKER_BOOTSTRAP_ADMIN_*`, `SPRING_MAIL_*`, `TRACKER_APP_BASE_URL`.
  - **Encryption key backup and rotation**: add a new key id, switch the active id, and keep the old key for decryption.
  - The accepted risks: no 2FA, data kept indefinitely, and the legal review before go-live.
- [X] T106 Verify that the single-jar build works with `cd backend && ./mvnw -Pbundle-frontend package`, then run `java -jar backend/target/*.jar` with the prod-like env vars and confirm the SPA loads at `/`. Add an SPA fallback controller `backend/src/main/java/com/example/tracker/config/SpaForwardController.java` that forwards non-`/api` routes to `index.html`.
- [X] T107 Run every scenario in `specs/001-employee-tracker/quickstart.md`, including the encryption-at-rest check, and the full `./mvnw verify`, `npm test` and `npx playwright test`. Fix any failures.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup and **blocks all stories**.
- **US1 (Phase 3)**: depends on Foundational only.
- **US2 (Phase 4)**: depends on Foundational. It reuses `EmployeeService`, `EmployeeController` and `EmployeeViewPolicy` from US1 (T053–T055), so build it after US1 or coordinate on those files.
- **US3 (Phase 5)**: depends on Foundational and US1's `EmployeeService` and admin view (T052–T055). It doesn't depend on US2.
- **US4 (Phase 6)**: depends on Foundational and US2's `EmployeeSpecifications` (T079) for export filters. Headcount (T094) depends only on Foundational.
- **Polish (Phase 7)**: depends on all stories.

```text
Setup ─► Foundational ─► US1 ─┬─► US2 ─► US4 (export)
                              └─► US3
                 Foundational ─► US4 (headcount only)
```

### Within each story

Tests (fail first) → migrations and entities → services → controllers → frontend.

## Parallel Execution Examples

**Phase 2**: after T013 and T019–T022, run T025, T027, T039–T042, T045 and T046 together.

**US1**:

```text
Tests:     T048 ‖ T049 ‖ T050
Records:   T051 ‖ T052  →  T053 → T054 → T055
Sensitive: T056 → T057 ‖ T059 ‖ T060 ‖ T061 ‖ T062 → T058 → T063 → T064
Frontend:  T066 ‖ T067 ‖ T069 ‖ T070 ‖ T071 ‖ T073  →  T068 → T072
```

**US2**: run T074, T075 and T076 together, then T077, then T078 → T079 → T080 → T081. Frontend T082 and T083 can run together, then T084 → T085 → T086.

**US3**: T087 first, then T088 → T089. T090 and T091 can run together, then T092.

**US4**: T093 first, then T094 and T095 together, then T096. T097 can run alongside, then T098.

## Implementation Strategy

### MVP (recommended: Foundational + US1 + US2)

US1 alone works and can be tested, but admins can only reach records from the create screen. Shipping US2 with it gives a usable product: a searchable directory with self-service.

1. Phase 1 → Phase 2. **Validate**: the sign-in scenarios in quickstart.
2. Phase 3 (US1). **Validate**: quickstart US1.
3. Phase 4 (US2). **Validate**: quickstart US2 and `VisibilityMatrixIT`. Demo or deploy.

### Incremental delivery

4. US3 for offboarding, which also closes access for leavers. It should ship before real terminations happen in production.
5. US4 for reporting.
6. Polish, including performance at 5,000 employees and single-jar packaging.

## Notes

- [P] means different files and no dependency on unfinished tasks.
- Never let any class other than `EmployeeViewPolicy` build an employee response DTO, and never let any package other than `sensitive` read the sensitive tables.
- Commit after each task or logical group, and stop at each checkpoint to validate.

---

## Implementation Notes (recorded during /speckit-implement, 2026-11-12)

Where the build differs from the task text, and why:

- **Analysis fixes built in**:
  - **U1**: sessions are keyed by account id, not username, so an email change can't hide a session from revocation. Tested in `LifecycleIT.sessionsEndEvenAfterAnEmailChange`.
  - **U2**: terminating the last enabled admin's employee record is blocked with `LAST_ADMIN`; terminating your own record is blocked with `SELF_LOCKOUT`.
  - **A1**: admin-only rules are explicit (method, path) pairs in `SecurityConfig`.
  - **T1**: Spring's SPA CSRF handler, plus a filter that issues the `XSRF-TOKEN` cookie on every request.
- **T048**: contract assertions (`ContractAssert.matchesContract()`) are made inside every integration test instead of a separate `EmployeeAdminContractIT`. Each employee, reference-data and history endpoint is checked there.
- **T058/T059/T060**: encrypted columns are plain `byte[]` and are decrypted only by `FieldCipher`, through `SensitiveDataService`. There is no JPA `AttributeConverter`. A converter decrypts on every entity load, so a missing key would have broken the masked view. This way it keeps working, which is quickstart check "wrong key".
- **T052/T077**: there is one `EmployeeView` record, rather than one DTO class per view. Only `EmployeeViewPolicy` builds it, and it fills in only the fields the view allows. `VisibilityMatrixIT` enforces this.
- **Sensitive `version`**: the API's `version` for sensitive data is 0 when nothing is stored and 1 or more once saved, so a stale "first save" can't overwrite existing data.
- **Login failures**: a locked account returns the same generic 401 as any other failure, without a separate `ACCOUNT_LOCKED` code. Otherwise the response would reveal that the account exists (spec edge case). The contract was updated to match.
- **Contract changes made while implementing**:
  - `allOf` replaced with flattened schemas, and `oneOf [X, null]` replaced with `anyOf`, so the validator can check responses strictly.
  - `Problem.employeeCount` added.
  - `govIdType` and `bankName` added to the access-log `field` enum.
- **Jackson**: unknown JSON fields are rejected everywhere (`fail-on-unknown-properties: true`), so PATCH bodies can't sneak in `status` or other fields.
- **Local port**: `docker-compose.yml` reads the host port from `TRACKER_DB_PORT`, because 5432 was taken on the build machine.
- **Verification run**:
  - `./mvnw verify`: 61 integration tests passed.
  - `npm test`: 4 component tests passed.
  - `npx playwright test`: 14 end-to-end tests passed against the dev stack.
  - `-Pbundle-frontend package`: the jar serves the SPA and API.
- **Not covered by automated tests**: the timed SC-001 (adding an employee in under 3 minutes) and SC-006 (90% of first-time admins succeed) need people to measure.
