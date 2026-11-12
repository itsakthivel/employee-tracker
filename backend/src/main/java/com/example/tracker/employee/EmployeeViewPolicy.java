package com.example.tracker.employee;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.Ref;
import com.example.tracker.employee.dto.EmergencyContact;
import com.example.tracker.employee.dto.EmployeeView;
import com.example.tracker.employee.dto.EmployeeView.View;
import com.example.tracker.history.EmployeeSnapshot;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The only place employee responses are built (FR-003, FR-006, FR-019). Directory: colleagues'
 * work details. Self: the employee's own record without notes. Admin: every field. Sensitive data
 * is never part of an employee response.
 */
@Component
public class EmployeeViewPolicy {

  private final EmployeeRepository employees;

  public EmployeeViewPolicy(EmployeeRepository employees) {
    this.employees = employees;
  }

  /** The profile the user may see, or 404 if the employee is hidden from them. */
  public EmployeeView viewFor(CurrentUser user, Employee e) {
    if (user.isAdmin()) {
      return build(e, View.ADMIN, true);
    }
    if (user.isSelf(e.getId())) {
      return build(e, View.SELF, true);
    }
    if (!e.isTerminated()) {
      return build(e, View.DIRECTORY, true);
    }
    throw ApiException.notFound("Employee");
  }

  /** The employee's own record as they see it, even when they are an administrator. */
  public EmployeeView selfView(Employee e) {
    return build(e, View.SELF, true);
  }

  /** A list item: no direct reports. */
  public EmployeeView summaryFor(CurrentUser user, Employee e) {
    return build(e, user.isAdmin() ? View.ADMIN : View.DIRECTORY, false);
  }

  /** History fields the user may see: null means all; 404 if they may not see this history. */
  public Set<String> visibleHistoryFields(CurrentUser user, Employee e) {
    if (user.isAdmin()) {
      return null;
    }
    if (user.isSelf(e.getId())) {
      return EmployeeSnapshot.SELF_VIEW_FIELDS;
    }
    throw ApiException.notFound("Employee");
  }

  private EmployeeView build(Employee e, View view, boolean withReports) {
    boolean self = view == View.SELF;
    boolean admin = view == View.ADMIN;
    boolean summaryOnly = !withReports;
    List<Ref> reports =
        withReports
            ? employees.findByManagerIdOrderByFullNameAsc(e.getId()).stream()
                .filter(r -> !r.isTerminated())
                .map(r -> new Ref(r.getId(), r.getFullName()))
                .toList()
            : null;
    boolean personal = (admin || self) && !summaryOnly;
    EmergencyContact contact =
        personal
                && (e.getEmergencyContactName() != null
                    || e.getEmergencyContactRelationship() != null
                    || e.getEmergencyContactPhone() != null)
            ? new EmergencyContact(
                e.getEmergencyContactName(),
                e.getEmergencyContactRelationship(),
                e.getEmergencyContactPhone())
            : null;
    return new EmployeeView(
        e.getId(),
        view,
        e.getFullName(),
        e.getWorkEmail(),
        e.getPhone(),
        e.getJobTitle(),
        ref(e.getDepartment().getId(), e.getDepartment().getName()),
        e.getLocation() == null ? null : ref(e.getLocation().getId(), e.getLocation().getName()),
        e.getManager() == null ? null : ref(e.getManager().getId(), e.getManager().getFullName()),
        admin || self ? e.getEmployeeNumber() : null,
        admin || self ? e.getStatus() : null,
        admin || self ? e.getEmploymentType() : null,
        admin || self ? e.getStartDate() : null,
        admin ? e.getEndDate() : null,
        admin ? e.isPreHire() : null,
        reports,
        personal ? e.getVersion() : null,
        personal ? e.getDateOfBirth() : null,
        personal ? e.getHomeAddress() : null,
        contact,
        admin && !summaryOnly ? e.getNotes() : null);
  }

  private static Ref ref(java.util.UUID id, String name) {
    return new Ref(id, name);
  }
}
