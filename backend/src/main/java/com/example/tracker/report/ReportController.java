package com.example.tracker.report;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeFilter;
import com.example.tracker.employee.EmployeeRepository;
import com.example.tracker.employee.EmployeeSpecifications;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (enforced in SecurityConfig). Exports never include sensitive data (FR-006b). */
@RestController
public class ReportController {

  static final List<String> COLUMNS =
      List.of(
          "Employee ID",
          "Full name",
          "Work email",
          "Phone",
          "Job title",
          "Department",
          "Location",
          "Manager",
          "Employment type",
          "Status",
          "Start date",
          "End date",
          "Date of birth",
          "Home address",
          "Emergency contact name",
          "Emergency contact relationship",
          "Emergency contact phone",
          "Notes");

  private static final int BATCH = 500;

  private final HeadcountService headcount;
  private final EmployeeRepository employees;
  private final Clock clock;

  public ReportController(HeadcountService headcount, EmployeeRepository employees, Clock clock) {
    this.headcount = headcount;
    this.employees = employees;
    this.clock = clock;
  }

  @GetMapping("/api/v1/reports/headcount")
  public HeadcountService.Headcount headcount() {
    return headcount.headcount();
  }

  @GetMapping("/api/v1/employees/export")
  public void export(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<UUID> departmentId,
      @RequestParam(required = false) List<UUID> locationId,
      @RequestParam(required = false) UUID managerId,
      @RequestParam(required = false) List<EmploymentStatus> status,
      @RequestParam(required = false) List<EmploymentType> employmentType,
      @RequestParam(defaultValue = "false") boolean includeTerminated,
      HttpServletResponse response)
      throws IOException {
    if (q != null && q.length() > 100) {
      new FieldErrors().add("q", "must be at most 100 characters").throwIfAny();
    }
    CurrentUser user = CurrentUser.get();
    var spec =
        EmployeeSpecifications.forFilter(
            user,
            new EmployeeFilter(q, departmentId, locationId, managerId, status, employmentType, includeTerminated));
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"employees-" + LocalDate.now(clock) + ".csv\"");
    try (var csv = new CsvWriter(response.getOutputStream())) {
      csv.row(COLUMNS);
      Sort order = Sort.by("fullName").and(Sort.by("id"));
      for (int page = 0; ; page++) {
        var batch = employees.findAll(spec, PageRequest.of(page, BATCH, order));
        for (Employee e : batch) {
          csv.row(row(e));
        }
        if (!batch.hasNext()) {
          break;
        }
      }
    }
  }

  private static List<String> row(Employee e) {
    List<String> r = new ArrayList<>();
    r.add(e.getEmployeeNumber());
    r.add(e.getFullName());
    r.add(e.getWorkEmail());
    r.add(e.getPhone());
    r.add(e.getJobTitle());
    r.add(e.getDepartment().getName());
    r.add(e.getLocation() == null ? null : e.getLocation().getName());
    r.add(e.getManager() == null ? null : e.getManager().getFullName());
    r.add(e.getEmploymentType().name());
    r.add(e.getStatus().name());
    r.add(String.valueOf(e.getStartDate()));
    r.add(e.getEndDate() == null ? null : e.getEndDate().toString());
    r.add(e.getDateOfBirth() == null ? null : e.getDateOfBirth().toString());
    r.add(e.getHomeAddress());
    r.add(e.getEmergencyContactName());
    r.add(e.getEmergencyContactRelationship());
    r.add(e.getEmergencyContactPhone());
    r.add(e.getNotes());
    return r;
  }
}
