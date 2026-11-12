package com.example.tracker.history;

import com.example.tracker.employee.Employee;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Display values of every tracked employee field, used to diff before and after a change. */
public final class EmployeeSnapshot {

  /** Fields an employee may see in their own history (the self view). */
  public static final Set<String> SELF_VIEW_FIELDS =
      Set.of(
          "employeeNumber",
          "fullName",
          "workEmail",
          "phone",
          "jobTitle",
          "department",
          "location",
          "manager",
          "employmentType",
          "status",
          "startDate",
          "dateOfBirth",
          "homeAddress",
          "emergencyContactName",
          "emergencyContactRelationship",
          "emergencyContactPhone");

  private EmployeeSnapshot() {}

  public static Map<String, String> of(Employee e) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("employeeNumber", e.getEmployeeNumber());
    m.put("fullName", e.getFullName());
    m.put("workEmail", e.getWorkEmail());
    m.put("phone", e.getPhone());
    m.put("jobTitle", e.getJobTitle());
    m.put("department", e.getDepartment() == null ? null : e.getDepartment().getName());
    m.put("location", e.getLocation() == null ? null : e.getLocation().getName());
    m.put("manager", e.getManager() == null ? null : e.getManager().getFullName());
    m.put("employmentType", str(e.getEmploymentType()));
    m.put("status", str(e.getStatus()));
    m.put("startDate", str(e.getStartDate()));
    m.put("endDate", str(e.getEndDate()));
    m.put("dateOfBirth", str(e.getDateOfBirth()));
    m.put("homeAddress", e.getHomeAddress());
    m.put("emergencyContactName", e.getEmergencyContactName());
    m.put("emergencyContactRelationship", e.getEmergencyContactRelationship());
    m.put("emergencyContactPhone", e.getEmergencyContactPhone());
    m.put("notes", e.getNotes());
    return m;
  }

  public static Map<String, String[]> diff(Map<String, String> before, Map<String, String> after) {
    Map<String, String[]> changes = new LinkedHashMap<>();
    after.forEach(
        (field, value) -> {
          if (!Objects.equals(before.get(field), value)) {
            changes.put(field, new String[] {before.get(field), value});
          }
        });
    return changes;
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }
}
