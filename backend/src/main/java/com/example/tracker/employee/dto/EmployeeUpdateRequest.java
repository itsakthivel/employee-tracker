package com.example.tracker.employee.dto;

import com.example.tracker.employee.EmploymentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;

/** PATCH body: absent fields are left alone, explicit nulls clear optional fields. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class EmployeeUpdateRequest {
  public Long version;
  public JsonNullable<String> employeeNumber = JsonNullable.undefined();
  public JsonNullable<String> fullName = JsonNullable.undefined();
  public JsonNullable<String> workEmail = JsonNullable.undefined();
  public JsonNullable<String> phone = JsonNullable.undefined();
  public JsonNullable<String> jobTitle = JsonNullable.undefined();
  public JsonNullable<UUID> departmentId = JsonNullable.undefined();
  public JsonNullable<UUID> locationId = JsonNullable.undefined();
  public JsonNullable<UUID> managerId = JsonNullable.undefined();
  public JsonNullable<EmploymentType> employmentType = JsonNullable.undefined();
  public JsonNullable<LocalDate> startDate = JsonNullable.undefined();
  public JsonNullable<LocalDate> dateOfBirth = JsonNullable.undefined();
  public JsonNullable<String> homeAddress = JsonNullable.undefined();
  public JsonNullable<EmergencyContact> emergencyContact = JsonNullable.undefined();
  public JsonNullable<String> notes = JsonNullable.undefined();
}
