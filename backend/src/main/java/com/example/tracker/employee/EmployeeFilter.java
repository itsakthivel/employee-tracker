package com.example.tracker.employee;

import java.util.List;
import java.util.UUID;

/** List, search and export filters (FR-013, FR-016–FR-018). */
public record EmployeeFilter(
    String q,
    List<UUID> departmentId,
    List<UUID> locationId,
    UUID managerId,
    List<EmploymentStatus> status,
    List<EmploymentType> employmentType,
    boolean includeTerminated) {}
