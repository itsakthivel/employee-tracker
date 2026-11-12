# Implementation Plan: Employee Tracker

**Branch**: `001-employee-tracker` | **Date**: 2026-11-12 (revised after the clarification session) | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-employee-tracker/spec.md`

## Summary

A web app where HR administrators keep employee records and run the employment lifecycle (hire, leave, terminate, rehire). Administrators also hold each employee's sensitive data (compensation history, government ID, tax ID, bank details): it is masked by default, encrypted in storage, and every reveal is logged. Every employee signs in with a tracker password to browse a company directory and update their own phone and emergency contact. Headcount and CSV export are admin-only.

The technical approach is a Spring Boot 3 (Java 21) REST API on PostgreSQL, with a React + TypeScript single-page app bundled into the same jar. Sessions are stored server-side so they can be revoked. A single view policy (directory, self or admin) decides which fields every response contains. Sensitive data lives in separate tables with application-level AES-GCM encryption. Details are in [research.md](./research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.x (frontend)

**Primary Dependencies**:
- Backend: Spring Boot 3.3 (Web MVC, Data JPA, Security, Validation, Mail, Session JDBC), Flyway, springdoc-openapi
- Frontend: React 18, Vite, React Router, TanStack Query, React Hook Form + Zod, Mantine

**Storage**: PostgreSQL 16 (`pg_trgm`); sensitive columns encrypted at the application level with AES-256-GCM; sessions in PostgreSQL

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL, Mailpit), MockMvc contract tests, a role × endpoint × field visibility matrix; Vitest + React Testing Library; Playwright

**Target Platform**: Linux server or container on JVM 21; desktop browsers (latest Chrome, Edge, Firefox and Safari); an SMTP server for password-reset email

**Project Type**: Web application (backend API + frontend SPA, shipped as one jar)

**Performance Goals**: Directory search and filter < 1 s at 5,000 employees (SC-003); profile load < 1 s

**Constraints**:
- No CORS (single origin).
- No deletion of employee records, and sensitive data is kept indefinitely.
- Sensitive data never leaves through non-admin responses or exports.
- Password-only sign-in (no second factor in v1).
- No bulk import.

**Scale/Scope**: One organization, ≤ 5,000 employees (each with a sign-in), roughly 50–200 concurrent sessions at peak, about 11 screens (sign-in, change/reset password, directory, profile, my profile, add/edit employee, sensitive data panel, history, access log, overview, departments/locations, users)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is still the blank template, so there are no ratified principles to check against. **Result: PASS (no gates defined).**

Practices this plan follows anyway:
- Tests come before or alongside code, and each user story is independently testable.
- The contract is the source of truth, and contract tests enforce it.
- Two projects only (backend, frontend), with no layers beyond controller, service and repository.
- Security rules (visibility, masking, logging) are enforced in one place each and covered by automated tests.

**Post-design re-check (after Phase 1)**: PASS. The revision adds a `sensitive` package and an `account` package inside the existing backend, not new projects or services.

## Project Structure

### Documentation (this feature)

```text
specs/001-employee-tracker/
├── plan.md              # This file
├── research.md          # Phase 0: decisions and rationale
├── data-model.md        # Phase 1: entities, visibility, state machine
├── quickstart.md        # Phase 1: run and validation guide
├── contracts/
│   └── openapi.yaml     # Phase 1: REST API contract (v1.1.0)
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 (/speckit-tasks, not created yet)
```

### Source Code (repository root)

```text
docker-compose.yml                     # PostgreSQL 16 + Mailpit for local development

backend/
├── pom.xml                            # Also builds frontend/ into static resources
├── src/main/java/com/example/tracker/
│   ├── TrackerApplication.java
│   ├── config/                        # SecurityConfig, session/CSRF, ProblemDetail handlers, OpenAPI
│   ├── auth/                          # Login, logout, me, change-password, lockout,
│   │                                  #   must-change-password filter, password policy + common list
│   ├── account/                       # UserAccount admin endpoints, temporary passwords,
│   │                                  #   password-reset tokens + mail, session revocation
│   ├── employee/                      # Employee entity, Specifications, service, controller,
│   │                                  #   EmployeeViewPolicy + Directory/Self/Admin DTOs, self-update
│   ├── lifecycle/                     # Status change, reactivate (disables/enables accounts)
│   ├── sensitive/                     # EmployeeSensitive, CompensationRecord, AES-GCM converter,
│   │                                  #   KeyProvider, masking, reveal, SensitiveAccessLog
│   ├── history/                       # EmployeeChange + field-diff recorder
│   ├── reference/                     # Department, Location
│   └── report/                        # Headcount, CSV export
├── src/main/resources/
│   ├── application.yml
│   ├── common-passwords.txt           # 100k list for FR-003d
│   ├── db/migration/                  # Flyway V1__init.sql, ...
│   └── db/seed/                       # dev + perf seed (profile-gated)
└── src/test/java/com/example/tracker/
    ├── contract/                      # Responses checked against contracts/openapi.yaml
    ├── security/                      # Visibility matrix, lockout, reset, session revocation
    ├── integration/                   # Testcontainers: cycles, history, locking, encryption
    └── unit/                          # Validation, state machine, masking, CSV escaping

frontend/
├── package.json
├── vite.config.ts                     # /api proxy to :8080
├── src/
│   ├── api/                           # Typed client from openapi.yaml + query hooks
│   ├── auth/                          # SignIn, ChangePassword, ForgotPassword, ResetPassword, guards
│   ├── pages/                         # Directory, EmployeeProfile, MyProfile, EmployeeForm,
│   │                                  #   Overview, AccessLog, Departments, Locations, Users
│   ├── components/                    # FilterBar, EmployeeTable, StatusDialog, HistoryList,
│   │                                  #   SensitivePanel (masked + Reveal), CompensationTable
│   └── lib/                           # Zod schemas mirroring server validation
└── tests/
    ├── unit/
    └── e2e/                           # Playwright: one spec per user story + auth
```

**Structure Decision**: Web application with `backend/` (Spring Boot) and `frontend/` (React + Vite). Backend code is grouped by feature. `sensitive/` is kept apart so its encryption, masking and logging stay in one package that is easy to review, and no other package reads its tables directly. The Maven build packages the frontend into the jar.

### User story → module map

| Story | Backend | Frontend |
|-------|---------|----------|
| Foundation: sign-in and accounts (blocks all stories) | `auth`, `account`, `config` | auth pages, Users |
| US1 Records (P1) | `employee`, `history`, `reference`, `sensitive` | EmployeeForm, EmployeeProfile, SensitivePanel, CompensationTable, AccessLog |
| US2 Directory and self-service (P2) | `employee` (view policy, search, self-update) | Directory, FilterBar, EmployeeProfile, MyProfile |
| US3 Lifecycle (P3) | `lifecycle` (+ `account` session revocation) | StatusDialog, reactivate flow |
| US4 Overview and export (P4) | `report` | Overview, Export |

## Key Risks

| Risk | Mitigation |
|------|------------|
| A sensitive or personal field leaks to non-admins | Separate tables; view-specific DTOs built only by `EmployeeViewPolicy`; a visibility-matrix test fails the build on any leak (SC-009) |
| Encryption key lost or exposed | Key held outside the database and repository; key id stored per row for rotation; documented backup of the key; masked values keep working from last4 without the key |
| Password-only admin access to bank and ID data (accepted risk) | Lockout, idle timeout, reveal logging; TOTP can be added behind the existing `AuthenticationProvider` |
| A terminated employee keeps access | Sessions are stored server-side and revoked in the same transaction as the status change; integration test |
| Password-reset email not delivered in production | SMTP settings validated at startup; admins can always issue a temporary password instead |
| JDK 11 on the dev machine | Install JDK 21; the Maven Enforcer plugin fails fast |
| Manual entry of up to 5,000 employees at go-live (accepted in spec) | Fast keyboard-friendly form with "Save and add another"; flagged for go-live planning |

## Complexity Tracking

No constitution violations to justify.
