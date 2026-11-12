# Feature Specification: Employee Tracker

**Feature Branch**: `001-employee-tracker`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "I want to build employee tracker and add necessary details"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Add and maintain employee records (Priority: P1)

An HR administrator adds a new employee to the tracker by entering their details: full name, employee ID, work email, phone number, job title, department, manager, employment type, start date and employment status, plus compensation, government ID, tax ID and bank details where needed. Later, the administrator updates those details when something changes, such as a promotion, a department transfer or a new phone number.

**Why this priority**: The tracker is only useful once employee records exist. Without a reliable way to create and update records, nothing else in the product works.

**Independent Test**: An administrator can add an employee, see the saved record with every entered detail, edit one field and see the change, all without any other feature built.

**Acceptance Scenarios**:

1. **Given** an administrator is on the add-employee form, **When** they fill in all required details and save, **Then** the employee record is created and shown with every entered value.
2. **Given** an administrator leaves a required field empty or enters an invalid email, **When** they try to save, **Then** the record is not saved and each problem field is clearly identified.
3. **Given** an employee ID already belongs to another employee, **When** the administrator tries to save a new record with that ID, **Then** the save is rejected with a message saying the ID is already in use.
4. **Given** an existing employee record, **When** the administrator changes the job title and saves, **Then** the record shows the new title and the change is recorded in the record's history (what changed, who changed it, when).
5. **Given** an employee with a bank account number recorded, **When** an administrator opens their profile, **Then** the account number shows only its last 4 characters. **When** the administrator chooses Reveal, **Then** the full number is shown and the reveal is recorded in the sensitive data access log.

---

### User Story 2 - Find and view employees (Priority: P2)

Any signed-in user browses the company directory of employees, searches by name, employee ID or email, and filters by department, employment status or manager. They open an employee to see their profile. Employees see directory details only (name, job title, department, location, manager, work email, phone). Administrators see every detail.

**Why this priority**: Once records exist, finding the right employee quickly is the most frequent everyday task.

**Independent Test**: With a set of sample employees loaded, a user can search for one by name, filter the list by department, and open a profile showing the correct details.

**Acceptance Scenarios**:

1. **Given** at least 50 employees exist, **When** a user types part of an employee's name, **Then** the list shows only matching employees.
2. **Given** employees in several departments, **When** a user filters by one department, **Then** only that department's employees are listed, with a count of results.
3. **Given** a search or filter matches nobody, **When** results are shown, **Then** the user sees a clear "no employees found" message and a way to clear the filters.
4. **Given** a user opens an employee from the list, **When** the profile loads, **Then** it shows the details that user is allowed to see, including the employee's manager and direct reports.
5. **Given** a signed-in employee who is not an Administrator, **When** they open another employee's profile, **Then** date of birth, home address, emergency contact and notes are not shown, and there are no edit controls.
6. **Given** a signed-in employee, **When** they open their own profile and change their phone number or emergency contact and save, **Then** the change is saved and recorded in the history. All other fields on their own profile are read-only.

---

### User Story 3 - Track employment status changes and offboarding (Priority: P3)

An administrator records changes to an employee's status (Active, On Leave, Terminated). When an employee leaves, the administrator marks them as Terminated with an end date. The record is kept for reference but is excluded from the default active list.

**Why this priority**: Accurate headcount and history depend on lifecycle tracking, but the tracker already delivers value with basic records and search.

**Independent Test**: An administrator can change an employee to On Leave, then to Terminated with an end date, and confirm the employee no longer appears in the default list but can still be found with the "include former employees" filter.

**Acceptance Scenarios**:

1. **Given** an Active employee, **When** the administrator marks them as Terminated with an end date, **Then** their status shows Terminated and they disappear from the default active list.
2. **Given** a Terminated employee, **When** a user turns on the "include former employees" filter, **Then** that employee appears with their end date.
3. **Given** an employee who manages others, **When** the administrator tries to terminate them, **Then** the system warns that their direct reports need a new manager and lists those reports.

---

### User Story 4 - Headcount overview and export (Priority: P4)

An administrator views a summary of headcount by department, status and employment type, and exports the current filtered employee list to a spreadsheet-compatible file.

**Why this priority**: Reporting is valuable for planning but depends on the earlier stories being in place.

**Independent Test**: With sample data loaded, a user can open the overview and see headcount totals that match the underlying records, then export a filtered list and confirm the file contains the same employees.

**Acceptance Scenarios**:

1. **Given** employees across departments, **When** a user opens the overview, **Then** they see the total active headcount and a breakdown by department, status and employment type.
2. **Given** a filtered employee list, **When** the user exports it, **Then** the exported file contains exactly the filtered employees and the visible columns.

---

### Edge Cases

- Two employees share the same full name: they are told apart by employee ID and email in all lists and search results.
- Someone tries to make an employee their own manager, or to set up a circular reporting chain (A manages B, B manages A): the system rejects it.
- A department is renamed or removed while employees are still assigned to it: existing employees keep a valid department, and a department with employees assigned cannot be removed.
- The start date is in the future (a pre-hire): the record is allowed and shown as "Starting on [date]".
- The end date is before the start date: the system rejects it.
- Two administrators edit the same record at the same time: the second save is warned that the record changed since it was opened, so it does not silently overwrite the first.
- A non-Administrator tries to reach sensitive data by any route (profile, search, export, history): nothing sensitive is returned.
- Someone tries to guess a password: the account locks after 5 failed attempts, and the message does not reveal whether the username exists.
- A rehired former employee: the administrator can reactivate the existing record rather than creating a duplicate.

## Requirements *(mandatory)*

### Functional Requirements

**Scope and access**

- **FR-001**: The tracker MUST cover employee records and their employment lifecycle (status changes, offboarding, rehire). Attendance, leave management and task or work-progress tracking are out of scope for v1.
- **FR-002**: The system MUST require users to sign in before they can see any employee information.
- **FR-003**: Access MUST be role-based with two roles. **Administrators** can view and edit all employee records, departments and locations, and use headcount and export. **Employees** (every active staff member with a sign-in) can search and view the company directory, limited to directory details: full name, job title, department, location, manager, direct reports, work email and phone. They can view all of their own details and edit only their own phone number and emergency contact. Terminated employees are excluded from the directory for non-Administrators.
- **FR-003a**: An employee's sign-in MUST stop working when their status becomes Terminated, and work again if they are reactivated.
- **FR-003b**: Users MUST sign in with a username (their work email) and a password specific to the tracker. Single sign-on is out of scope for v1.
- **FR-003c**: Administrators MUST be able to create a sign-in for an employee (linked to that employee's record), choose the role, and set a temporary password. The user MUST change the temporary password at first sign-in.
- **FR-003d**: Passwords MUST be at least 12 characters and MUST NOT be a commonly used password. Users MUST be able to change their own password.
- **FR-003e**: Users MUST be able to reset a forgotten password through a single-use link sent to their work email that expires after 1 hour. Administrators MUST also be able to issue a new temporary password.
- **FR-003f**: After 5 failed sign-in attempts in a row, the account MUST be locked for 15 minutes. Sessions MUST end after 30 minutes of inactivity.

**Employee records**

- **FR-004**: Administrators MUST be able to create an employee record with these required details: full name, employee ID, work email, job title, department, employment type (full-time, part-time, contractor, intern), start date and employment status.
- **FR-005**: Administrators MUST be able to record these optional details: phone number, manager, work location, date of birth, home address, emergency contact (name, relationship, phone) and notes.
- **FR-006**: Personal fields (date of birth, home address, emergency contact) and notes MUST be visible only to Administrators, except that employees can see their own personal fields (not notes).
- **FR-006a**: Administrators MUST be able to record sensitive data for an employee: compensation (amount, currency, pay frequency, effective date), government ID (type and number), tax identification number, and bank details (account holder name, bank name, account number, bank routing code).
- **FR-006b**: Sensitive data MUST be visible only to Administrators. It MUST never appear in the directory, in another employee's view, or in exports. Employees MUST NOT see sensitive data, including their own, in v1.
- **FR-006c**: Government ID, tax ID and bank account numbers MUST be shown masked (only the last 4 characters visible) by default. An Administrator MUST take an explicit "reveal" action to see the full value.
- **FR-006d**: Every reveal of a masked value and every change to sensitive data MUST be logged with who, which employee, which field and when. Only Administrators can view this log. Change history entries for masked fields MUST store masked values only.
- **FR-006e**: Compensation changes MUST be kept as a dated history (each entry has an effective date), so past compensation can be viewed.
- **FR-006f**: Sensitive data MUST be protected at rest and in transit, so that it is unreadable to anyone with access to stored data or backups but without application-level permission.
- **FR-007**: The system MUST validate that the employee ID and work email are unique, that the email and phone number are in a valid format, and that dates are valid.
- **FR-008**: Administrators MUST be able to edit any employee detail. Every change MUST be logged with the previous value, the new value, who changed it and when.
- **FR-009**: Users MUST be able to view an employee's change history from their profile.
- **FR-010**: The system MUST prevent a manager assignment that would make an employee their own manager or create a circular reporting chain.
- **FR-011**: The system MUST NOT permanently delete employee records through normal use. Employees leave by being marked Terminated, and their record is kept.

**Status and lifecycle**

- **FR-012**: The system MUST support the statuses Active, On Leave and Terminated. Terminated requires an end date on or after the start date.
- **FR-013**: Lists MUST show only Active and On Leave employees by default, with an option to include Terminated employees.
- **FR-014**: When an administrator terminates an employee who has direct reports, the system MUST warn them and list the reports that need a new manager.
- **FR-015**: Administrators MUST be able to reactivate a Terminated employee's record for a rehire.

**Search, view and reporting**

- **FR-016**: Users MUST be able to search employees by name, employee ID or email, with partial matches.
- **FR-017**: Users MUST be able to filter employees by department, status, employment type, manager and location, and to combine filters.
- **FR-018**: Users MUST be able to sort the employee list by name, department, start date or status.
- **FR-019**: The employee profile MUST show all details the user is allowed to see, the employee's manager and their direct reports.
- **FR-020**: Administrators MUST be able to see headcount totals by department, status and employment type.
- **FR-021**: Administrators MUST be able to export the current filtered employee list to a spreadsheet-compatible file.

**Reference data**

- **FR-022**: Administrators MUST be able to manage the list of departments and work locations used when entering employee details.
- **FR-023**: The system MUST block removing a department or location while employees are still assigned to it.

### Key Entities

- **Employee**: A person employed or contracted by the organization. Holds identity details (name, employee ID, email, phone), job details (title, department, location, employment type, manager), lifecycle details (status, start date, end date) and personal details (date of birth, address, emergency contact, notes).
- **Department**: An organizational unit that employees belong to. Has a name and, optionally, a department head (an Employee).
- **Location**: A work site or "Remote" that an employee is assigned to.
- **Reporting relationship**: Links an employee to their manager (another Employee), forming the organization's reporting structure.
- **Compensation record**: One dated compensation entry for an employee: amount, currency, pay frequency and effective date. An employee has many, and the latest effective one is current.
- **Sensitive data access log entry**: Records that an Administrator revealed or changed a sensitive field: who, which employee, which field, what action and when.
- **Change history entry**: A record of a single change to an employee record: which field changed, the old and new values, who made the change and when.
- **User / Role**: A person who signs in to the tracker, linked to their own Employee record. The role (Administrator or Employee) decides what they can see and change.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can add a new employee with all required details in under 3 minutes.
- **SC-002**: Users can find a specific employee by name or ID in under 10 seconds from the employee list.
- **SC-003**: Search and filter results appear within 1 second for an organization of up to 5,000 employees.
- **SC-004**: 100% of changes to employee records appear in the change history with who, what and when.
- **SC-005**: Headcount totals in the overview match the underlying employee records exactly.
- **SC-006**: 90% of first-time administrators complete adding and updating an employee without help or documentation.
- **SC-007**: No duplicate employee IDs or work emails exist in the tracker at any time.
- **SC-008**: 100% of reveals of masked values and changes to sensitive data appear in the sensitive data access log.
- **SC-009**: In access testing, 0 sensitive values are visible to non-Administrators on any screen, search result or export.

## Clarifications

### Session 2026-11-12

- Q: Which kinds of tracking are in scope? → A: Employee records and lifecycle only (no attendance, leave or tasks).
- Q: Who can see and edit what? → A: Administrators edit everything; every employee can browse a company directory (name, title, department, contact details) and update their own phone and emergency contact. No separate manager role.
- Q: Store sensitive personal data? → A: Yes: salary/compensation, government ID or tax numbers, and bank details. Administrator-only, masked by default, with every reveal and change logged.
- Q: How should employees sign in? → A: Tracker-specific username and password for everyone; Administrators create the accounts.
- Q: How do existing employees get into the tracker at go-live? → A: Manual entry only, one employee and one account at a time; no bulk import in v1.
- Q: What happens to a former employee's sensitive data after they leave? → A: Kept forever, like the rest of the record; no erasure in v1.

## Assumptions

- The tracker is for a single organization (not multi-company) with up to about 5,000 employees.
- It is used through a web browser on desktop and laptop. A dedicated mobile experience is out of scope for v1, but pages should remain usable on smaller screens.
- Every employee has a work email address that can receive password-reset messages, and the tracker can send email.
- Sign-in uses a password only, with no second factor, even for Administrators who can reveal bank and ID data. This is an accepted risk for v1, partly offset by masking, reveal logging, lockout and session timeouts. Two-factor sign-in for Administrators is recommended as a follow-up.
- Employee records, including former employees' compensation, government ID, tax ID and bank details, are kept indefinitely while the organization uses the tracker. There is no automatic or manual erasure in v1. Privacy-law erasure requests are out of scope for v1, subject to the legal review noted below.
- Bulk import of employees or sign-in accounts (for example, from a spreadsheet) is out of scope for v1, including for the first load at go-live. Administrators enter each employee and create each sign-in one at a time, and the go-live timeline must allow for this.
- The organization will confirm with its legal or privacy team which data-protection law applies (for example rules on consent, retention and breach notification) before go-live. The safeguards in FR-006a–f are the baseline.
- Compensation is recorded for reference only. Payroll processing, performance reviews, recruiting and benefits administration are out of scope.
- Employee photos and document attachments (contracts, certificates) are out of scope for v1.
- The tracker uses one language and one date format, set for the organization.
