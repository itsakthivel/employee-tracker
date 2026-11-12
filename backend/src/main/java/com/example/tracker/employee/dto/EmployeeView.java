package com.example.tracker.employee.dto;

import com.example.tracker.common.Ref;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An employee as one viewer may see it. Only {@code EmployeeViewPolicy} builds these; fields the
 * viewer may not see are left null and are omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeView(
    UUID id,
    View view,
    String fullName,
    String workEmail,
    String phone,
    String jobTitle,
    Ref department,
    Ref location,
    Ref manager,
    String employeeNumber,
    EmploymentStatus status,
    EmploymentType employmentType,
    LocalDate startDate,
    LocalDate endDate,
    Boolean isPreHire,
    List<Ref> directReports,
    Long version,
    LocalDate dateOfBirth,
    String homeAddress,
    EmergencyContact emergencyContact,
    String notes) {

  public enum View {
    DIRECTORY,
    SELF,
    ADMIN
  }
}
