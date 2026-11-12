package com.example.tracker.lifecycle;

import com.example.tracker.account.Role;
import com.example.tracker.account.SessionRevoker;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.common.Ref;
import com.example.tracker.common.Validators;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import com.example.tracker.employee.EmployeeService;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.history.ChangeRecorder;
import com.example.tracker.history.ChangeType;
import com.example.tracker.history.EmployeeSnapshot;
import com.example.tracker.reference.DepartmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-012–FR-015 and FR-003a: status changes, offboarding and rehire. */
@Service
public class LifecycleService {

  public record StatusChange(
      Long version, EmploymentStatus status, LocalDate endDate, Boolean acknowledgeDirectReports) {}

  public static class Reactivation {
    public Long version;
    public LocalDate startDate;
    public String jobTitle;
    public UUID departmentId;
    public JsonNullable<UUID> managerId = JsonNullable.undefined();
  }

  private final EmployeeRepository employees;
  private final EmployeeService employeeService;
  private final DepartmentRepository departments;
  private final UserAccountRepository accounts;
  private final SessionRevoker revoker;
  private final ChangeRecorder history;

  public LifecycleService(
      EmployeeRepository employees,
      EmployeeService employeeService,
      DepartmentRepository departments,
      UserAccountRepository accounts,
      SessionRevoker revoker,
      ChangeRecorder history) {
    this.employees = employees;
    this.employeeService = employeeService;
    this.departments = departments;
    this.accounts = accounts;
    this.revoker = revoker;
    this.history = history;
  }

  @Transactional
  public Employee changeStatus(UUID id, StatusChange req, CurrentUser actor) {
    Employee e = employeeService.get(id);
    checkVersion(e, req.version());
    if (req.status() == null) {
      new FieldErrors().add("status", "is required").throwIfAny();
    }
    if (e.isTerminated()) {
      throw new ApiException(
          ErrorCode.INVALID_TRANSITION, "A terminated employee can only be reactivated");
    }
    if (req.status() == e.getStatus()) {
      throw new ApiException(ErrorCode.INVALID_TRANSITION, "The employee already has that status");
    }
    var before = EmployeeSnapshot.of(e);

    if (req.status() == EmploymentStatus.TERMINATED) {
      if (req.endDate() == null) {
        new FieldErrors().add("endDate", "is required to terminate").throwIfAny();
      }
      if (req.endDate().isBefore(e.getStartDate())) {
        throw new ApiException(ErrorCode.END_BEFORE_START, "The end date is before the start date");
      }
      if (actor.isSelf(id)) {
        throw new ApiException(ErrorCode.SELF_LOCKOUT, "You cannot terminate your own record");
      }
      List<Ref> reports =
          employees.findByManagerIdOrderByFullNameAsc(id).stream()
              .filter(r -> !r.isTerminated())
              .map(r -> new Ref(r.getId(), r.getFullName()))
              .toList();
      if (!reports.isEmpty() && !Boolean.TRUE.equals(req.acknowledgeDirectReports())) {
        throw new ApiException(
            ErrorCode.DIRECT_REPORTS,
            reports.size() + " direct report(s) will need a new manager",
            Map.of("directReports", reports));
      }
      var account = accounts.findByEmployeeId(id).orElse(null);
      if (account != null
          && account.isEnabled()
          && account.getRole() == Role.ADMIN
          && accounts.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
        throw new ApiException(
            ErrorCode.LAST_ADMIN,
            "This employee's sign-in is the last enabled administrator. Add another administrator first.");
      }
      e.setStatus(EmploymentStatus.TERMINATED);
      e.setEndDate(req.endDate());
      if (account != null) {
        account.setEnabled(false);
        revoker.revokeAll(account.getId());
      }
    } else {
      e.setStatus(req.status());
    }
    employees.flush();
    history.recordDiff(id, before, EmployeeSnapshot.of(e), ChangeType.STATUS_CHANGED, actor.accountId());
    return e;
  }

  @Transactional
  public Employee reactivate(UUID id, Reactivation req, CurrentUser actor) {
    Employee e = employeeService.get(id);
    checkVersion(e, req.version);
    if (!e.isTerminated()) {
      throw new ApiException(ErrorCode.INVALID_TRANSITION, "Only a terminated employee can be reactivated");
    }
    var errors = new FieldErrors();
    if (req.startDate == null) {
      errors.add("startDate", "is required");
    }
    String title = Validators.trimToNull(req.jobTitle);
    if (req.jobTitle != null) {
      Validators.requireLength(errors, "jobTitle", title, 1, 100, true);
    }
    var department = req.departmentId == null ? null : departments.findById(req.departmentId).orElse(null);
    if (req.departmentId != null && department == null) {
      errors.add("departmentId", "does not exist");
    }
    Employee manager = null;
    if (req.managerId.isPresent() && req.managerId.get() != null) {
      manager = employees.findById(req.managerId.get()).orElse(null);
      if (manager == null || manager.isTerminated()) {
        errors.add("managerId", "must be a current employee");
      }
    }
    errors.throwIfAny();
    if (req.startDate.isBefore(e.getEndDate())) {
      throw new ApiException(
          ErrorCode.END_BEFORE_START, "The new start date must be on or after the previous end date");
    }
    if (manager != null) {
      employeeService.checkManager(id, manager.getId());
    }

    var before = EmployeeSnapshot.of(e);
    e.setStatus(EmploymentStatus.ACTIVE);
    e.setEndDate(null);
    e.setStartDate(req.startDate);
    if (title != null) {
      e.setJobTitle(title);
    }
    if (department != null) {
      e.setDepartment(department);
    }
    if (req.managerId.isPresent()) {
      e.setManager(manager);
    }
    employees.flush();
    history.recordDiff(id, before, EmployeeSnapshot.of(e), ChangeType.REACTIVATED, actor.accountId());

    accounts
        .findByEmployeeId(id)
        .ifPresent(
            account -> {
              account.setEnabled(true);
              account.setMustChangePassword(true);
            });
    return e;
  }

  private static void checkVersion(Employee e, Long version) {
    if (version == null) {
      new FieldErrors().add("version", "is required").throwIfAny();
    }
    if (version != e.getVersion()) {
      throw new ApiException(
          ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and try again.");
    }
  }
}
