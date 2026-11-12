package com.example.tracker.employee.dto;

import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EmployeeCreateRequest(
    String employeeNumber,
    String fullName,
    String workEmail,
    String phone,
    String jobTitle,
    UUID departmentId,
    UUID locationId,
    UUID managerId,
    EmploymentType employmentType,
    EmploymentStatus status,
    LocalDate startDate,
    LocalDate dateOfBirth,
    String homeAddress,
    EmergencyContact emergencyContact,
    String notes) {}
