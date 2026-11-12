package com.example.tracker.account;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.auth.PasswordPolicy;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.common.Ref;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-003c/FR-003e: administrators manage sign-in accounts. */
@Service
public class AccountService {

  public record AccountView(
      UUID id,
      String username,
      Role role,
      Ref employee,
      boolean enabled,
      boolean mustChangePassword,
      boolean locked,
      Instant lastLoginAt) {}

  private final UserAccountRepository accounts;
  private final EmployeeRepository employees;
  private final PasswordEncoder encoder;
  private final PasswordPolicy policy;
  private final SessionRevoker revoker;
  private final Clock clock;

  public AccountService(
      UserAccountRepository accounts,
      EmployeeRepository employees,
      PasswordEncoder encoder,
      PasswordPolicy policy,
      SessionRevoker revoker,
      Clock clock) {
    this.accounts = accounts;
    this.employees = employees;
    this.encoder = encoder;
    this.policy = policy;
    this.revoker = revoker;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AccountView> list(String q, Role role, Boolean enabled) {
    String term = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
    return accounts.findAllByOrderByUsernameAsc().stream()
        .filter(a -> role == null || a.getRole() == role)
        .filter(a -> enabled == null || a.isEnabled() == enabled)
        .filter(
            a ->
                term == null
                    || term.isEmpty()
                    || a.getUsername().contains(term)
                    || (a.getEmployee() != null
                        && a.getEmployee().getFullName().toLowerCase(Locale.ROOT).contains(term)))
        .map(this::view)
        .toList();
  }

  @Transactional
  public AccountView create(UUID employeeId, Role role, String temporaryPassword) {
    var errors = new FieldErrors();
    if (employeeId == null) {
      errors.add("employeeId", "is required");
    }
    if (role == null) {
      errors.add("role", "is required");
    }
    policy.check(errors, "temporaryPassword", temporaryPassword);
    errors.throwIfAny();

    Employee employee =
        employees.findById(employeeId).orElseThrow(() -> ApiException.notFound("Employee"));
    if (employee.isTerminated()) {
      throw new ApiException(
          ErrorCode.EMPLOYEE_TERMINATED, "A terminated employee cannot be given a sign-in");
    }
    if (accounts.existsByEmployeeId(employeeId)) {
      throw new ApiException(ErrorCode.ACCOUNT_EXISTS, "This employee already has a sign-in");
    }
    if (accounts.existsByUsernameIgnoreCase(employee.getWorkEmail())) {
      throw new ApiException(ErrorCode.DUPLICATE_EMAIL, "That work email is already a username");
    }
    var account = new UserAccount(employee.getWorkEmail(), encoder.encode(temporaryPassword), role, employee);
    account.setMustChangePassword(true);
    return view(accounts.save(account));
  }

  @Transactional
  public AccountView update(UUID id, Role role, Boolean enabled) {
    CurrentUser actor = CurrentUser.get();
    UserAccount account = accounts.findWithEmployeeById(id).orElseThrow(() -> ApiException.notFound("Account"));
    boolean demoting = role != null && role != Role.ADMIN && account.getRole() == Role.ADMIN;
    boolean disabling = Boolean.FALSE.equals(enabled) && account.isEnabled();

    if (account.getId().equals(actor.accountId()) && (demoting || disabling)) {
      throw new ApiException(
          ErrorCode.SELF_LOCKOUT, "You cannot disable or demote your own account");
    }
    if ((demoting || disabling)
        && account.getRole() == Role.ADMIN
        && account.isEnabled()
        && accounts.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
      throw new ApiException(ErrorCode.LAST_ADMIN, "At least one enabled administrator is required");
    }
    if (role == Role.EMPLOYEE && account.getEmployee() == null) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "An account without an employee record must stay an administrator");
    }
    if (Boolean.TRUE.equals(enabled)
        && account.getEmployee() != null
        && account.getEmployee().isTerminated()) {
      throw new ApiException(
          ErrorCode.EMPLOYEE_TERMINATED, "Reactivate the employee before enabling their sign-in");
    }
    if (role != null) {
      account.setRole(role);
    }
    if (enabled != null) {
      account.setEnabled(enabled);
    }
    if (disabling) {
      revoker.revokeAll(account.getId());
    }
    return view(account);
  }

  @Transactional
  public void issueTemporaryPassword(UUID id, String temporaryPassword) {
    policy.validate("temporaryPassword", temporaryPassword);
    UserAccount account = accounts.findById(id).orElseThrow(() -> ApiException.notFound("Account"));
    account.setPassword(encoder.encode(temporaryPassword), true);
    revoker.revokeAll(account.getId());
  }

  private AccountView view(UserAccount a) {
    Employee e = a.getEmployee();
    return new AccountView(
        a.getId(),
        a.getUsername(),
        a.getRole(),
        e == null ? null : new Ref(e.getId(), e.getFullName()),
        a.isEnabled(),
        a.isMustChangePassword(),
        a.isLocked(clock.instant()),
        a.getLastLoginAt());
  }
}
