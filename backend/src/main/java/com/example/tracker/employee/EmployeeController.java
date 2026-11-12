package com.example.tracker.employee;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.employee.dto.EmployeeCreateRequest;
import com.example.tracker.employee.dto.EmployeeUpdateRequest;
import com.example.tracker.employee.dto.EmployeeView;
import com.example.tracker.history.HistoryService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

  public record EmployeePage(
      List<EmployeeView> items, int page, int size, long totalItems, int totalPages) {}

  private static final Map<String, String> SORTS =
      Map.of(
          "fullName", "fullName",
          "department", "department.name",
          "startDate", "startDate",
          "status", "status");

  private final EmployeeService service;
  private final EmployeeRepository employees;
  private final EmployeeViewPolicy policy;
  private final HistoryService history;

  public EmployeeController(
      EmployeeService service,
      EmployeeRepository employees,
      EmployeeViewPolicy policy,
      HistoryService history) {
    this.service = service;
    this.employees = employees;
    this.policy = policy;
    this.history = history;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public EmployeePage list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<UUID> departmentId,
      @RequestParam(required = false) List<UUID> locationId,
      @RequestParam(required = false) UUID managerId,
      @RequestParam(required = false) List<EmploymentStatus> status,
      @RequestParam(required = false) List<EmploymentType> employmentType,
      @RequestParam(defaultValue = "false") boolean includeTerminated,
      @RequestParam(defaultValue = "fullName") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    CurrentUser user = CurrentUser.get();
    var errors = new FieldErrors();
    if (q != null && q.length() > 100) {
      errors.add("q", "must be at most 100 characters");
    }
    if (page < 0) {
      errors.add("page", "must be 0 or more");
    }
    if (size < 1 || size > 100) {
      errors.add("size", "must be between 1 and 100");
    }
    Sort order = sort(sort, user, errors);
    errors.throwIfAny();

    var filter =
        new EmployeeFilter(q, departmentId, locationId, managerId, status, employmentType, includeTerminated);
    var result =
        employees.findAll(
            EmployeeSpecifications.forFilter(user, filter), PageRequest.of(page, size, order));
    return new EmployeePage(
        result.stream().map(e -> policy.summaryFor(user, e)).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  /** Shared with export: same sort rules. */
  static Sort sort(String sort, CurrentUser user, FieldErrors errors) {
    boolean desc = sort.startsWith("-");
    String key = desc ? sort.substring(1) : sort;
    String property = SORTS.get(key);
    if (property == null) {
      errors.add("sort", "is not a supported sort");
      return Sort.unsorted();
    }
    if (!user.isAdmin() && (key.equals("startDate") || key.equals("status"))) {
      errors.add("sort", "is only available to administrators");
      return Sort.unsorted();
    }
    Sort primary = desc ? Sort.by(property).descending() : Sort.by(property).ascending();
    return primary.and(Sort.by("fullName")).and(Sort.by("id"));
  }

  @PostMapping
  @Transactional
  public ResponseEntity<EmployeeView> create(@RequestBody EmployeeCreateRequest body) {
    CurrentUser user = CurrentUser.get();
    Employee created = service.create(body, user);
    return ResponseEntity.created(URI.create("/api/v1/employees/" + created.getId()))
        .body(policy.viewFor(user, created));
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public EmployeeView get(@PathVariable UUID id) {
    return policy.viewFor(CurrentUser.get(), service.get(id));
  }

  @PatchMapping("/{id}")
  @Transactional
  public EmployeeView update(@PathVariable UUID id, @RequestBody EmployeeUpdateRequest body) {
    CurrentUser user = CurrentUser.get();
    return policy.viewFor(user, service.update(id, body, user));
  }

  @GetMapping("/{id}/history")
  @Transactional(readOnly = true)
  public HistoryService.HistoryPage history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    var errors = new FieldErrors();
    if (page < 0) {
      errors.add("page", "must be 0 or more");
    }
    if (size < 1 || size > 100) {
      errors.add("size", "must be between 1 and 100");
    }
    errors.throwIfAny();
    CurrentUser user = CurrentUser.get();
    Employee employee = service.get(id);
    var fields = policy.visibleHistoryFields(user, employee);
    return history.page(id, fields, page, size);
  }
}
