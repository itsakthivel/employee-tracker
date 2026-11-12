# Data Model: Employee Tracker

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md) | **Date**: 2026-11-12 (revised after the clarification session)

Storage is PostgreSQL. Every table has `id` (UUID primary key), `created_at` and `updated_at` (timestamptz) unless stated otherwise. 🔒 marks a field encrypted at the application level (research §5).

## Entity overview

```text
Department 1 ──< Employee >── 0..1 Location
                  │  ▲
        manager_id│  │  self-reference (reporting relationship)
                  └──┘
Employee 1 ── 0..1 EmployeeSensitive
Employee 1 ──< CompensationRecord
Employee 1 ──< EmployeeChange >── UserAccount (changed_by)
Employee 1 ──< SensitiveAccessLog >── UserAccount (actor)
Employee 1 ── 0..1 UserAccount 1 ──< PasswordResetToken
Department ── 0..1 Employee (head)
```

## Employee

The **View** column says who sees each field (research §4): **D** = directory (anyone signed in), **S** = the employee viewing their own record, **A** = Admin.

| Field | Type | Req. | Rules | View |
|-------|------|------|-------|------|
| employee_number | text | yes | Unique (case-insensitive), 1–20 chars, `[A-Za-z0-9-]` | S, A |
| full_name | text | yes | 1–150 chars, trimmed | D, S, A |
| work_email | text | yes | Valid email, unique (case-insensitive), stored lowercase; also the username | D, S, A |
| phone | text | no | `+` and 7–15 digits; spaces/dashes accepted on input. **Self-editable** | D, S, A |
| job_title | text | yes | 1–100 chars | D, S, A |
| department_id | FK → Department | yes | | D, S, A |
| location_id | FK → Location | no | | D, S, A |
| manager_id | FK → Employee | no | Not self; not in own subtree (FR-010); not Terminated | D, S, A |
| employment_type | enum | yes | `FULL_TIME`, `PART_TIME`, `CONTRACTOR`, `INTERN` | S, A |
| status | enum | yes | `ACTIVE`, `ON_LEAVE`, `TERMINATED` (state machine below) | S, A |
| start_date | date | yes | Future allowed (pre-hire) | S, A |
| end_date | date | cond. | Required if and only if `TERMINATED`; ≥ start_date | A |
| date_of_birth | date | no | In the past; age ≥ 14 at start_date | S, A |
| home_address | text | no | ≤ 500 chars | S, A |
| emergency_contact_name | text | no | ≤ 150 chars. **Self-editable** | S, A |
| emergency_contact_relationship | text | no | ≤ 50 chars. **Self-editable** | S, A |
| emergency_contact_phone | text | no | Same format as phone. **Self-editable** | S, A |
| notes | text | no | ≤ 2,000 chars | A |
| version | bigint | sys | Optimistic lock | S, A |

**Derived**: `directReports` (visible in D, S and A; for non-admins, only non-terminated reports are listed), `isPreHire`.

**Directory visibility**: non-admins can only see employees with status `ACTIVE` or `ON_LEAVE`. Anything else is returned as 404.

**Indexes**: unique `lower(employee_number)` and `lower(work_email)`; btree on `department_id`, `location_id`, `manager_id`, `status`; GIN `pg_trgm` on `full_name`, `employee_number` and `work_email`.

**Deletion**: none (FR-011).

### Status state machine (FR-012–FR-015, FR-003a)

```text
  create ─► ACTIVE ◄──── return ──── ON_LEAVE
              │  └──── leave ─────────► │
              │                         │
              └──► TERMINATED ◄─────────┘
                       │
                       └── reactivate ──► ACTIVE  (new start_date ≥ old end_date;
                                                   end_date cleared)
```

| Transition | Side effects |
|------------|--------------|
| → `TERMINATED` | `end_date` required. With direct reports, `acknowledgeDirectReports=true` is required (otherwise 409 `DIRECT_REPORTS`). The linked UserAccount is disabled and all its sessions are revoked. |
| `TERMINATED` → `ACTIVE` (reactivate) | The linked UserAccount is re-enabled with `must_change_password=true`. |
| create | Allowed as `ACTIVE` or `ON_LEAVE` only. |
| No-op (same status) | Rejected with 422 `INVALID_TRANSITION`. |

## EmployeeSensitive (one per employee, Admin only)

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| employee_id | FK → Employee | yes | Unique |
| gov_id_type | text | cond. | Required if `gov_id_number` is set; free text ≤ 50 (e.g. "Passport", "Aadhaar", "SSN") |
| gov_id_number 🔒 | bytea | no | Plain value 4–30 chars, `[A-Za-z0-9-]` |
| gov_id_last4 | text | sys | Last 4 characters of the plain value |
| tax_id 🔒 | bytea | no | Plain value 4–30 chars |
| tax_id_last4 | text | sys | |
| bank_account_holder 🔒 | bytea | no | ≤ 150 chars |
| bank_name | text | no | ≤ 150 chars |
| bank_account_number 🔒 | bytea | no | Plain value 4–34 chars (covers IBAN) |
| bank_account_last4 | text | sys | |
| bank_routing_code 🔒 | bytea | no | Plain value ≤ 20 chars (IFSC, sort code, ABA, SWIFT) |
| bank_routing_last4 | text | sys | |
| key_id | text | sys | Encryption key id used for this row |
| version | bigint | sys | Optimistic lock |

**Masking (FR-006c)**: maskable fields (gov ID, tax ID, account number, routing code) are returned as `••••` + last4. The account holder is shown only through a reveal. The bank name is not masked.

## CompensationRecord (many per employee, Admin only, FR-006e)

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| employee_id | FK → Employee | yes | |
| amount 🔒 | bytea | yes | Plain value is a decimal > 0, with up to 2 decimals |
| currency | char(3) | yes | ISO 4217 |
| pay_frequency | enum | yes | `ANNUAL`, `MONTHLY`, `HOURLY` |
| effective_date | date | yes | Unique per employee |
| reason | text | no | ≤ 200 chars (e.g. "Promotion") |
| created_by | FK → UserAccount | yes | |

**Current compensation** is the record with the latest `effective_date` ≤ today. Records with a future effective date are allowed. Records are append-only: a correction is entered as a new record. Amounts are not masked, but because they're admin-only and encrypted, viewing the compensation list counts as a `REVEAL` of `compensation` in the access log.

## SensitiveAccessLog (append-only, Admin only, FR-006d)

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| actor_id | FK → UserAccount | yes | |
| employee_id | FK → Employee | yes | |
| field | text | yes | `govIdNumber`, `taxId`, `bankAccountHolder`, `bankAccountNumber`, `bankRoutingCode`, `compensation` |
| action | enum | yes | `REVEAL`, `CREATE`, `UPDATE` |
| occurred_at | timestamptz | yes | Server time |

No `updated_at`; rows are never updated or deleted. Index on `(employee_id, occurred_at desc)` and `(actor_id, occurred_at desc)`.

## EmployeeChange (append-only, FR-008, FR-009)

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| employee_id | FK → Employee | yes | |
| change_type | enum | yes | `CREATED`, `UPDATED`, `STATUS_CHANGED`, `REACTIVATED`, `SELF_UPDATED` |
| field_name | text | cond. | Null for `CREATED` |
| old_value / new_value | text | no | Display strings. Sensitive fields store the **masked** form only |
| changed_by | FK → UserAccount | yes | |
| changed_at | timestamptz | yes | |

**Visibility**: Admins see all entries. An employee sees entries for their own record whose `field_name` is in the self view. Index on `(employee_id, changed_at desc)`.

## Department / Location

| Entity | Field | Rules |
|--------|-------|-------|
| Department | name | Unique (case-insensitive), 1–100 |
| Department | head_employee_id | Optional; not Terminated |
| Location | name | Unique (case-insensitive), 1–100 (e.g. "Head Office", "Remote") |

Deleting a department or location is blocked while any employee references it (409 `IN_USE`, FR-023).

## UserAccount

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| username | text | yes | Unique (case-insensitive). Equals the linked employee's work email; updated automatically if the email changes |
| password_hash | text | yes | BCrypt (cost 12) |
| role | enum | yes | `ADMIN`, `EMPLOYEE` |
| employee_id | FK → Employee | cond. | Required for `EMPLOYEE`; optional for `ADMIN` (lets a bootstrap admin exist without a record); unique |
| enabled | boolean | yes | Set false when the linked employee is terminated (FR-003a) or by an admin |
| must_change_password | boolean | yes | True after an admin creates the account or issues a temporary password |
| failed_attempts | int | yes | Default 0; reset on success |
| locked_until | timestamptz | no | Set to now + 15 min on the 5th consecutive failure |
| password_changed_at | timestamptz | yes | |
| last_login_at | timestamptz | no | |

**Password rules (FR-003d)**: at least 12 characters, at most 128, and not in the common-password list (case-insensitive).

## PasswordResetToken (FR-003e)

| Field | Type | Req. | Rules |
|-------|------|------|-------|
| user_id | FK → UserAccount | yes | |
| token_hash | char(64) | yes | SHA-256 of the emailed token; unique |
| expires_at | timestamptz | yes | created_at + 1 hour |
| used_at | timestamptz | no | Set when used; a used token is rejected |

Issuing a new token invalidates the user's earlier unused tokens.

## Sessions

Managed by Spring Session JDBC (`spring_session`, `spring_session_attributes` tables, created by Flyway). Indexed by principal name so all of a user's sessions can be revoked at once. Idle timeout is 30 minutes.

## Requirement traceability

| Requirement | Enforced by |
|-------------|-------------|
| FR-003, FR-006, FR-006b, FR-019 | View column + `EmployeeViewPolicy`; directory visibility rule |
| FR-003a | Status transition side effects; session revocation |
| FR-003b–f | UserAccount, PasswordResetToken, Sessions |
| FR-004, FR-005, FR-007 | Employee rules and unique indexes |
| FR-006a, FR-006c, FR-006f | EmployeeSensitive 🔒 fields, last4 masking |
| FR-006d | SensitiveAccessLog; masked values in EmployeeChange |
| FR-006e | CompensationRecord |
| FR-008, FR-009 | EmployeeChange |
| FR-010 | manager_id subtree check |
| FR-011 | No delete operation |
| FR-012–FR-015 | Status state machine |
| FR-022, FR-023 | Department/Location delete guard |
