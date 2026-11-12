package com.example.tracker.history;

import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.common.Ref;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

  public record ChangeEntryView(
      UUID id,
      ChangeType changeType,
      String fieldName,
      String oldValue,
      String newValue,
      Ref changedBy,
      Instant changedAt) {}

  public record HistoryPage(List<ChangeEntryView> items, long totalItems) {}

  private final EmployeeChangeRepository changes;
  private final UserAccountRepository accounts;

  public HistoryService(EmployeeChangeRepository changes, UserAccountRepository accounts) {
    this.changes = changes;
    this.accounts = accounts;
  }

  /** {@code visibleFields} null means every field. */
  @Transactional(readOnly = true)
  public HistoryPage page(UUID employeeId, Set<String> visibleFields, int page, int size) {
    var pageable = PageRequest.of(page, size);
    Page<EmployeeChange> result =
        visibleFields == null
            ? changes.findByEmployeeIdOrderByChangedAtDesc(employeeId, pageable)
            : changes.findVisible(employeeId, visibleFields, pageable);
    var actorIds = result.stream().map(EmployeeChange::getChangedBy).collect(Collectors.toSet());
    Map<UUID, UserAccount> actors =
        accounts.findByIdIn(actorIds).stream()
            .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    var items =
        result.stream()
            .map(
                c ->
                    new ChangeEntryView(
                        c.getId(),
                        c.getChangeType(),
                        c.getFieldName(),
                        c.getOldValue(),
                        c.getNewValue(),
                        actorRef(c.getChangedBy(), actors.get(c.getChangedBy())),
                        c.getChangedAt()))
            .toList();
    return new HistoryPage(items, result.getTotalElements());
  }

  static Ref actorRef(UUID id, UserAccount a) {
    if (a == null) {
      return new Ref(id, "Unknown user");
    }
    return new Ref(id, a.getEmployee() != null ? a.getEmployee().getFullName() : a.getUsername());
  }
}
