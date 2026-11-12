package com.example.tracker.employee;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.FieldErrors;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** Builds the list/export query. Non-admins only ever see ACTIVE and ON_LEAVE employees. */
public final class EmployeeSpecifications {

  private static final Set<EmploymentStatus> CURRENT =
      EnumSet.of(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE);

  private EmployeeSpecifications() {}

  public static Specification<Employee> forFilter(CurrentUser user, EmployeeFilter f) {
    Set<EmploymentStatus> statuses = statuses(user, f);
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
      if (!q.isEmpty()) {
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Predicate> any = new ArrayList<>();
        any.add(cb.like(cb.lower(root.get("fullName")), like, '\\'));
        any.add(cb.like(cb.lower(root.get("workEmail")), like, '\\'));
        if (user.isAdmin()) {
          any.add(cb.like(cb.lower(root.get("employeeNumber")), like, '\\'));
        }
        where.add(cb.or(any.toArray(Predicate[]::new)));
      }
      if (f.departmentId() != null && !f.departmentId().isEmpty()) {
        where.add(root.get("department").get("id").in(f.departmentId()));
      }
      if (f.locationId() != null && !f.locationId().isEmpty()) {
        where.add(root.get("location").get("id").in(f.locationId()));
      }
      if (f.managerId() != null) {
        where.add(cb.equal(root.get("manager").get("id"), f.managerId()));
      }
      if (user.isAdmin() && f.employmentType() != null && !f.employmentType().isEmpty()) {
        where.add(root.get("employmentType").in(f.employmentType()));
      }
      if (statuses != null) {
        where.add(root.get("status").in(statuses));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  /** null means no status restriction (admin asking for everyone). */
  private static Set<EmploymentStatus> statuses(CurrentUser user, EmployeeFilter f) {
    boolean explicit = f.status() != null && !f.status().isEmpty();
    if (!user.isAdmin()) {
      if (explicit && f.status().contains(EmploymentStatus.TERMINATED)) {
        new FieldErrors().add("status", "must be ACTIVE or ON_LEAVE").throwIfAny();
      }
      return explicit ? EnumSet.copyOf(f.status()) : CURRENT;
    }
    if (explicit) {
      return EnumSet.copyOf(f.status());
    }
    return f.includeTerminated() ? null : CURRENT;
  }
}
