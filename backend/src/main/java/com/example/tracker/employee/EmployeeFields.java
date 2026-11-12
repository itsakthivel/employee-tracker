package com.example.tracker.employee;

import com.example.tracker.common.FieldErrors;
import com.example.tracker.common.Validators;
import com.example.tracker.employee.dto.EmergencyContact;
import com.example.tracker.employee.dto.EmployeeCreateRequest;
import com.example.tracker.employee.dto.EmployeeUpdateRequest;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;

/** Normalized editable employee fields plus the data-model.md rules that apply to them. */
record EmployeeFields(
    String employeeNumber,
    String fullName,
    String workEmail,
    String phone,
    String jobTitle,
    UUID departmentId,
    UUID locationId,
    UUID managerId,
    EmploymentType employmentType,
    LocalDate startDate,
    LocalDate dateOfBirth,
    String homeAddress,
    String emergencyContactName,
    String emergencyContactRelationship,
    String emergencyContactPhone,
    String notes) {

  static EmployeeFields from(EmployeeCreateRequest r) {
    EmergencyContact ec = r.emergencyContact();
    return normalize(
        new EmployeeFields(
            r.employeeNumber(),
            r.fullName(),
            r.workEmail(),
            r.phone(),
            r.jobTitle(),
            r.departmentId(),
            r.locationId(),
            r.managerId(),
            r.employmentType(),
            r.startDate(),
            r.dateOfBirth(),
            r.homeAddress(),
            ec == null ? null : ec.name(),
            ec == null ? null : ec.relationship(),
            ec == null ? null : ec.phone(),
            r.notes()));
  }

  static EmployeeFields of(Employee e) {
    return new EmployeeFields(
        e.getEmployeeNumber(),
        e.getFullName(),
        e.getWorkEmail(),
        e.getPhone(),
        e.getJobTitle(),
        e.getDepartment().getId(),
        e.getLocation() == null ? null : e.getLocation().getId(),
        e.getManager() == null ? null : e.getManager().getId(),
        e.getEmploymentType(),
        e.getStartDate(),
        e.getDateOfBirth(),
        e.getHomeAddress(),
        e.getEmergencyContactName(),
        e.getEmergencyContactRelationship(),
        e.getEmergencyContactPhone(),
        e.getNotes());
  }

  /** Current values overridden by whatever the PATCH body contains. */
  static EmployeeFields merge(Employee e, EmployeeUpdateRequest r) {
    EmployeeFields c = of(e);
    boolean ecSet = r.emergencyContact.isPresent();
    EmergencyContact ec = ecSet ? r.emergencyContact.get() : null;
    return normalize(
        new EmployeeFields(
            pick(r.employeeNumber, c.employeeNumber),
            pick(r.fullName, c.fullName),
            pick(r.workEmail, c.workEmail),
            pick(r.phone, c.phone),
            pick(r.jobTitle, c.jobTitle),
            pick(r.departmentId, c.departmentId),
            pick(r.locationId, c.locationId),
            pick(r.managerId, c.managerId),
            pick(r.employmentType, c.employmentType),
            pick(r.startDate, c.startDate),
            pick(r.dateOfBirth, c.dateOfBirth),
            pick(r.homeAddress, c.homeAddress),
            ecSet ? (ec == null ? null : ec.name()) : c.emergencyContactName,
            ecSet ? (ec == null ? null : ec.relationship()) : c.emergencyContactRelationship,
            ecSet ? (ec == null ? null : ec.phone()) : c.emergencyContactPhone,
            pick(r.notes, c.notes)));
  }

  EmployeeFields withContact(String phone, EmergencyContact ec, boolean ecSet) {
    return normalize(
        new EmployeeFields(
            employeeNumber,
            fullName,
            workEmail,
            phone,
            jobTitle,
            departmentId,
            locationId,
            managerId,
            employmentType,
            startDate,
            dateOfBirth,
            homeAddress,
            ecSet ? (ec == null ? null : ec.name()) : emergencyContactName,
            ecSet ? (ec == null ? null : ec.relationship()) : emergencyContactRelationship,
            ecSet ? (ec == null ? null : ec.phone()) : emergencyContactPhone,
            notes));
  }

  private static <T> T pick(JsonNullable<T> patch, T current) {
    return patch.isPresent() ? patch.get() : current;
  }

  private static EmployeeFields normalize(EmployeeFields f) {
    String email = Validators.trimToNull(f.workEmail);
    return new EmployeeFields(
        Validators.trimToNull(f.employeeNumber),
        Validators.trimToNull(f.fullName),
        email == null ? null : email.toLowerCase(Locale.ROOT),
        Validators.normalizePhone(f.phone),
        Validators.trimToNull(f.jobTitle),
        f.departmentId,
        f.locationId,
        f.managerId,
        f.employmentType,
        f.startDate,
        f.dateOfBirth,
        Validators.trimToNull(f.homeAddress),
        Validators.trimToNull(f.emergencyContactName),
        Validators.trimToNull(f.emergencyContactRelationship),
        Validators.normalizePhone(f.emergencyContactPhone),
        Validators.trimToNull(f.notes));
  }

  void validate(FieldErrors errors, LocalDate today) {
    if (employeeNumber == null) {
      errors.add("employeeNumber", "is required");
    } else if (!Validators.EMPLOYEE_NUMBER.matcher(employeeNumber).matches()) {
      errors.add("employeeNumber", "must be 1–20 letters, digits or dashes");
    }
    Validators.requireLength(errors, "fullName", fullName, 1, 150, true);
    if (workEmail == null) {
      errors.add("workEmail", "is required");
    } else if (workEmail.length() > 254 || !Validators.EMAIL.matcher(workEmail).matches()) {
      errors.add("workEmail", "must be a valid email address");
    }
    if (phone != null && !Validators.PHONE.matcher(phone).matches()) {
      errors.add("phone", "must start with + and have 7–15 digits");
    }
    Validators.requireLength(errors, "jobTitle", jobTitle, 1, 100, true);
    if (departmentId == null) {
      errors.add("departmentId", "is required");
    }
    if (employmentType == null) {
      errors.add("employmentType", "is required");
    }
    if (startDate == null) {
      errors.add("startDate", "is required");
    }
    if (dateOfBirth != null) {
      if (!dateOfBirth.isBefore(today)) {
        errors.add("dateOfBirth", "must be in the past");
      } else if (startDate != null && Period.between(dateOfBirth, startDate).getYears() < 14) {
        errors.add("dateOfBirth", "employee must be at least 14 on their start date");
      }
    }
    Validators.requireLength(errors, "homeAddress", homeAddress, 0, 500, false);
    Validators.requireLength(errors, "emergencyContact.name", emergencyContactName, 0, 150, false);
    Validators.requireLength(
        errors, "emergencyContact.relationship", emergencyContactRelationship, 0, 50, false);
    if (emergencyContactPhone != null && !Validators.PHONE.matcher(emergencyContactPhone).matches()) {
      errors.add("emergencyContact.phone", "must start with + and have 7–15 digits");
    }
    Validators.requireLength(errors, "notes", notes, 0, 2000, false);
  }
}
