package com.example.tracker.sensitive;

import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.common.Ref;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessLogService {

  public record AccessLogEntry(
      UUID id, Ref actor, Ref employee, String field, SensitiveAccessLog.Action action, Instant occurredAt) {}

  public record AccessLogPage(List<AccessLogEntry> items, long totalItems) {}

  private final SensitiveAccessLogRepository log;
  private final UserAccountRepository accounts;
  private final EmployeeRepository employees;

  public AccessLogService(
      SensitiveAccessLogRepository log, UserAccountRepository accounts, EmployeeRepository employees) {
    this.log = log;
    this.accounts = accounts;
    this.employees = employees;
  }

  @Transactional(readOnly = true)
  public AccessLogPage page(
      UUID employeeId, UUID actorId, Instant from, Instant to, int page, int size) {
    Specification<SensitiveAccessLog> spec =
        (root, query, cb) -> {
          List<Predicate> where = new ArrayList<>();
          if (employeeId != null) {
            where.add(cb.equal(root.get("employeeId"), employeeId));
          }
          if (actorId != null) {
            where.add(cb.equal(root.get("actorId"), actorId));
          }
          if (from != null) {
            where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
          }
          if (to != null) {
            where.add(cb.lessThan(root.get("occurredAt"), to));
          }
          return cb.and(where.toArray(Predicate[]::new));
        };
    var result =
        log.findAll(spec, PageRequest.of(page, size, Sort.by("occurredAt").descending().and(Sort.by("id"))));
    Map<UUID, UserAccount> actors =
        accounts.findByIdIn(result.stream().map(SensitiveAccessLog::getActorId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    Map<UUID, Employee> people =
        employees.findAllById(result.stream().map(SensitiveAccessLog::getEmployeeId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Employee::getId, Function.identity()));
    var items =
        result.stream()
            .map(
                e -> {
                  UserAccount a = actors.get(e.getActorId());
                  Employee p = people.get(e.getEmployeeId());
                  String actorName =
                      a == null ? "Unknown user" : a.getEmployee() != null ? a.getEmployee().getFullName() : a.getUsername();
                  return new AccessLogEntry(
                      e.getId(),
                      new Ref(e.getActorId(), actorName),
                      new Ref(e.getEmployeeId(), p == null ? "Unknown employee" : p.getFullName()),
                      e.getField(),
                      e.getAction(),
                      e.getOccurredAt());
                })
            .toList();
    return new AccessLogPage(items, result.getTotalElements());
  }
}
