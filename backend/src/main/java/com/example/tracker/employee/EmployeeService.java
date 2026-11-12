package com.example.tracker.employee;

import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.employee.dto.EmployeeCreateRequest;
import com.example.tracker.employee.dto.EmployeeUpdateRequest;
import com.example.tracker.employee.dto.SelfUpdateRequest;
import com.example.tracker.history.ChangeRecorder;
import com.example.tracker.history.ChangeType;
import com.example.tracker.history.EmployeeSnapshot;
import com.example.tracker.reference.DepartmentRepository;
import com.example.tracker.reference.LocationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and editing employee records (FR-004–FR-011). There is deliberately no delete. */
@Service
public class EmployeeService {

  private final EmployeeRepository employees;
  private final DepartmentRepository departments;
  private final LocationRepository locations;
  private final UserAccountRepository accounts;
  private final ChangeRecorder changes;
  private final Clock clock;

  public EmployeeService(
      EmployeeRepository employees,
      DepartmentRepository departments,
      LocationRepository locations,
      UserAccountRepository accounts,
      ChangeRecorder changes,
      Clock clock) {
    this.employees = employees;
    this.departments = departments;
    this.locations = locations;
    this.accounts = accounts;
    this.changes = changes;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Employee get(UUID id) {
    return employees.findById(id).orElseThrow(() -> ApiException.notFound("Employee"));
  }

  @Transactional
  public Employee create(EmployeeCreateRequest request, CurrentUser actor) {
    EmployeeFields fields = EmployeeFields.from(request);
    var errors = new FieldErrors();
    fields.validate(errors, today());
    if (request.status() == null) {
      errors.add("status", "is required");
    } else if (request.status() == EmploymentStatus.TERMINATED) {
      errors.add("status", "a new employee must be ACTIVE or ON_LEAVE");
    }
    errors.throwIfAny();
    checkUnique(fields, null);

    var employee = new Employee();
    apply(employee, fields);
    employee.setStatus(request.status());
    employees.saveAndFlush(employee);
    changes.recordCreated(employee.getId(), actor.accountId());
    return employee;
  }

  @Transactional
  public Employee update(UUID id, EmployeeUpdateRequest request, CurrentUser actor) {
    Employee employee = get(id);
    checkVersion(employee, request.version);
    var before = EmployeeSnapshot.of(employee);
    String oldEmail = employee.getWorkEmail();

    EmployeeFields fields = EmployeeFields.merge(employee, request);
    var errors = new FieldErrors();
    fields.validate(errors, today());
    errors.throwIfAny();
    checkUnique(fields, id);
    apply(employee, fields);
    employees.flush();

    changes.recordDiff(id, before, EmployeeSnapshot.of(employee), ChangeType.UPDATED, actor.accountId());
    syncUsername(employee, oldEmail);
    return employee;
  }

  /** An employee editing their own phone and emergency contact. */
  @Transactional
  public Employee updateSelf(SelfUpdateRequest request, CurrentUser actor) {
    if (actor.employeeId() == null) {
      throw ApiException.notFound("Employee record");
    }
    Employee employee = get(actor.employeeId());
    checkVersion(employee, request.version);
    var before = EmployeeSnapshot.of(employee);

    EmployeeFields fields =
        EmployeeFields.of(employee)
            .withContact(
                request.phone.isPresent() ? request.phone.get() : employee.getPhone(),
                request.emergencyContact.isPresent() ? request.emergencyContact.get() : null,
                request.emergencyContact.isPresent());
    var errors = new FieldErrors();
    fields.validate(errors, today());
    errors.throwIfAny();
    employee.setPhone(fields.phone());
    employee.setEmergencyContactName(fields.emergencyContactName());
    employee.setEmergencyContactRelationship(fields.emergencyContactRelationship());
    employee.setEmergencyContactPhone(fields.emergencyContactPhone());
    employees.flush();

    changes.recordDiff(
        employee.getId(), before, EmployeeSnapshot.of(employee), ChangeType.SELF_UPDATED, actor.accountId());
    return employee;
  }

  /** Rejects a manager that is the employee or sits anywhere below them (FR-010). */
  public void checkManager(UUID employeeId, UUID managerId) {
    if (managerId == null || employeeId == null) {
      return;
    }
    if (managerId.equals(employeeId) || employees.isInSubtree(employeeId, managerId)) {
      throw new ApiException(
          ErrorCode.REPORTING_CYCLE, "That manager would create a circular reporting line");
    }
  }

  void checkVersion(Employee employee, Long version) {
    if (version == null) {
      new FieldErrors().add("version", "is required").throwIfAny();
    }
    if (version != employee.getVersion()) {
      throw new ApiException(
          ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and try again.");
    }
  }

  private void checkUnique(EmployeeFields f, UUID selfId) {
    boolean numberTaken =
        selfId == null
            ? employees.existsByEmployeeNumberIgnoreCase(f.employeeNumber())
            : employees.existsByEmployeeNumberIgnoreCaseAndIdNot(f.employeeNumber(), selfId);
    if (numberTaken) {
      throw new ApiException(ErrorCode.DUPLICATE_EMPLOYEE_NUMBER, "Employee ID already in use");
    }
    boolean emailTaken =
        selfId == null
            ? employees.existsByWorkEmailIgnoreCase(f.workEmail())
            : employees.existsByWorkEmailIgnoreCaseAndIdNot(f.workEmail(), selfId);
    if (emailTaken) {
      throw new ApiException(ErrorCode.DUPLICATE_EMAIL, "Work email already in use");
    }
  }

  private void apply(Employee e, EmployeeFields f) {
    var errors = new FieldErrors();
    var department = departments.findById(f.departmentId()).orElse(null);
    if (department == null) {
      errors.add("departmentId", "does not exist");
    }
    var location = f.locationId() == null ? null : locations.findById(f.locationId()).orElse(null);
    if (f.locationId() != null && location == null) {
      errors.add("locationId", "does not exist");
    }
    Employee manager = f.managerId() == null ? null : employees.findById(f.managerId()).orElse(null);
    if (f.managerId() != null && manager == null) {
      errors.add("managerId", "does not exist");
    } else if (manager != null && manager.isTerminated()) {
      errors.add("managerId", "must be a current employee");
    }
    errors.throwIfAny();
    checkManager(e.getId(), f.managerId());

    e.setEmployeeNumber(f.employeeNumber());
    e.setFullName(f.fullName());
    e.setWorkEmail(f.workEmail());
    e.setPhone(f.phone());
    e.setJobTitle(f.jobTitle());
    e.setDepartment(department);
    e.setLocation(location);
    e.setManager(manager);
    e.setEmploymentType(f.employmentType());
    e.setStartDate(f.startDate());
    e.setDateOfBirth(f.dateOfBirth());
    e.setHomeAddress(f.homeAddress());
    e.setEmergencyContactName(f.emergencyContactName());
    e.setEmergencyContactRelationship(f.emergencyContactRelationship());
    e.setEmergencyContactPhone(f.emergencyContactPhone());
    e.setNotes(f.notes());
  }

  /** The sign-in username follows the work email. Sessions are keyed by account id, so they survive. */
  private void syncUsername(Employee employee, String oldEmail) {
    if (Objects.equals(oldEmail, employee.getWorkEmail())) {
      return;
    }
    accounts
        .findByEmployeeId(employee.getId())
        .ifPresent(
            account -> {
              var clash = accounts.findByUsernameIgnoreCase(employee.getWorkEmail());
              if (clash.isPresent() && !clash.get().getId().equals(account.getId())) {
                throw new ApiException(ErrorCode.DUPLICATE_EMAIL, "Work email already in use");
              }
              account.setUsername(employee.getWorkEmail());
            });
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
