package com.example.tracker.sensitive;

import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.common.Ref;
import com.example.tracker.common.Validators;
import com.example.tracker.employee.EmployeeRepository;
import com.example.tracker.history.ChangeRecorder;
import com.example.tracker.history.ChangeType;
import com.example.tracker.sensitive.CompensationRecord.PayFrequency;
import com.example.tracker.sensitive.SensitiveAccessLog.Action;
import com.example.tracker.sensitive.crypto.DataKeyProvider;
import com.example.tracker.sensitive.crypto.FieldCipher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-006a–FR-006f. Masked reads never decrypt. Every reveal and every change writes the access log
 * in the same transaction, and change history only ever stores masked values.
 */
@Service
public class SensitiveDataService {

  public record SensitiveMasked(
      long version,
      String govIdType,
      String govIdNumber,
      String taxId,
      String bankAccountHolder,
      String bankName,
      String bankAccountNumber,
      String bankRoutingCode) {}

  public record SensitiveInput(
      Long version,
      String govIdType,
      String govIdNumber,
      String taxId,
      String bankAccountHolder,
      String bankName,
      String bankAccountNumber,
      String bankRoutingCode) {}

  public record CompensationInput(
      String amount,
      String currency,
      PayFrequency payFrequency,
      LocalDate effectiveDate,
      String reason) {}

  public record CompensationView(
      UUID id,
      String amount,
      String currency,
      PayFrequency payFrequency,
      LocalDate effectiveDate,
      String reason,
      Ref createdBy,
      Instant createdAt) {}

  public record CompensationList(CompensationView current, List<CompensationView> items) {}

  /** Fields that can be revealed one at a time. */
  public static final List<String> REVEALABLE =
      List.of("govIdNumber", "taxId", "bankAccountHolder", "bankAccountNumber", "bankRoutingCode");

  private static final Pattern GOV_ID = Pattern.compile("^[A-Za-z0-9-]{4,30}$");
  private static final Pattern AMOUNT = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$");
  private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

  private final EmployeeSensitiveRepository sensitive;
  private final CompensationRecordRepository compensation;
  private final EmployeeRepository employees;
  private final UserAccountRepository accounts;
  private final SensitiveAccessLogger accessLog;
  private final ChangeRecorder history;
  private final FieldCipher cipher;
  private final DataKeyProvider keys;
  private final Clock clock;

  public SensitiveDataService(
      EmployeeSensitiveRepository sensitive,
      CompensationRecordRepository compensation,
      EmployeeRepository employees,
      UserAccountRepository accounts,
      SensitiveAccessLogger accessLog,
      ChangeRecorder history,
      FieldCipher cipher,
      DataKeyProvider keys,
      Clock clock) {
    this.sensitive = sensitive;
    this.compensation = compensation;
    this.employees = employees;
    this.accounts = accounts;
    this.accessLog = accessLog;
    this.history = history;
    this.cipher = cipher;
    this.keys = keys;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SensitiveMasked getMasked(UUID employeeId) {
    requireEmployee(employeeId);
    return sensitive.findByEmployeeId(employeeId).map(SensitiveDataService::masked).orElse(EMPTY);
  }

  @Transactional
  public SensitiveMasked replace(UUID employeeId, SensitiveInput in, CurrentUser actor) {
    requireEmployee(employeeId);
    Plain next = validate(in);
    EmployeeSensitive row = sensitive.findByEmployeeId(employeeId).orElse(null);
    long current = clientVersion(row);
    if (in.version() == null || in.version() != current) {
      throw new ApiException(
          ErrorCode.STALE_VERSION, "This record was changed by someone else. Reload and try again.");
    }
    Plain before = row == null ? Plain.EMPTY : decryptAll(row);
    if (row == null) {
      row = new EmployeeSensitive(employeeId);
    }

    record Change(String field, String oldValue, String newValue, String oldShown, String newShown) {}
    List<Change> changes =
        List.of(
            new Change("govIdType", before.govIdType, next.govIdType, before.govIdType, next.govIdType),
            new Change("govIdNumber", before.govIdNumber, next.govIdNumber, m(before.govIdNumber), m(next.govIdNumber)),
            new Change("taxId", before.taxId, next.taxId, m(before.taxId), m(next.taxId)),
            new Change(
                "bankAccountHolder",
                before.bankAccountHolder,
                next.bankAccountHolder,
                Masking.maskPresent(before.bankAccountHolder != null),
                Masking.maskPresent(next.bankAccountHolder != null)),
            new Change("bankName", before.bankName, next.bankName, before.bankName, next.bankName),
            new Change(
                "bankAccountNumber",
                before.bankAccountNumber,
                next.bankAccountNumber,
                m(before.bankAccountNumber),
                m(next.bankAccountNumber)),
            new Change(
                "bankRoutingCode",
                before.bankRoutingCode,
                next.bankRoutingCode,
                m(before.bankRoutingCode),
                m(next.bankRoutingCode)));

    // Re-encrypt everything with the active key so the row's key id is accurate.
    row.govIdType = next.govIdType;
    row.govIdNumber = cipher.encrypt(next.govIdNumber);
    row.govIdLast4 = Masking.last4(next.govIdNumber);
    row.taxId = cipher.encrypt(next.taxId);
    row.taxIdLast4 = Masking.last4(next.taxId);
    row.bankAccountHolder = cipher.encrypt(next.bankAccountHolder);
    row.bankName = next.bankName;
    row.bankAccountNumber = cipher.encrypt(next.bankAccountNumber);
    row.bankAccountLast4 = Masking.last4(next.bankAccountNumber);
    row.bankRoutingCode = cipher.encrypt(next.bankRoutingCode);
    row.bankRoutingLast4 = Masking.last4(next.bankRoutingCode);
    row.keyId = keys.activeKeyId();
    row = sensitive.saveAndFlush(row);

    for (Change c : changes) {
      if (!Objects.equals(c.oldValue(), c.newValue())) {
        accessLog.log(
            actor.accountId(), employeeId, c.field(), c.oldValue() == null ? Action.CREATE : Action.UPDATE);
        history.recordField(
            employeeId, c.field(), c.oldShown(), c.newShown(), ChangeType.UPDATED, actor.accountId());
      }
    }
    return masked(row);
  }

  @Transactional
  public String reveal(UUID employeeId, String field, CurrentUser actor) {
    requireEmployee(employeeId);
    if (field == null || !REVEALABLE.contains(field)) {
      new FieldErrors().add("field", "is not a revealable field").throwIfAny();
    }
    EmployeeSensitive row = sensitive.findByEmployeeId(employeeId).orElse(null);
    byte[] stored =
        row == null
            ? null
            : switch (field) {
              case "govIdNumber" -> row.govIdNumber;
              case "taxId" -> row.taxId;
              case "bankAccountHolder" -> row.bankAccountHolder;
              case "bankAccountNumber" -> row.bankAccountNumber;
              default -> row.bankRoutingCode;
            };
    String value = decrypt(stored);
    accessLog.log(actor.accountId(), employeeId, field, Action.REVEAL);
    return value;
  }

  @Transactional
  public CompensationList listCompensation(UUID employeeId, CurrentUser actor) {
    requireEmployee(employeeId);
    var records = compensation.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    Map<UUID, UserAccount> creators =
        accounts
            .findByIdIn(records.stream().map(CompensationRecord::getCreatedBy).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    List<CompensationView> items = records.stream().map(r -> view(r, creators)).toList();
    if (!items.isEmpty()) {
      accessLog.log(actor.accountId(), employeeId, "compensation", Action.REVEAL);
    }
    LocalDate today = LocalDate.now(clock);
    CompensationView current =
        items.stream().filter(i -> !i.effectiveDate().isAfter(today)).findFirst().orElse(null);
    return new CompensationList(current, items);
  }

  @Transactional
  public CompensationView addCompensation(UUID employeeId, CompensationInput in, CurrentUser actor) {
    requireEmployee(employeeId);
    var errors = new FieldErrors();
    String amount = Validators.trimToNull(in.amount());
    if (amount == null || !AMOUNT.matcher(amount).matches() || new BigDecimal(amount).signum() <= 0) {
      errors.add("amount", "must be a positive number with up to 2 decimals");
    }
    if (in.currency() == null || !CURRENCY.matcher(in.currency()).matches()) {
      errors.add("currency", "must be a 3-letter ISO 4217 code");
    }
    if (in.payFrequency() == null) {
      errors.add("payFrequency", "is required");
    }
    if (in.effectiveDate() == null) {
      errors.add("effectiveDate", "is required");
    }
    String reason = Validators.trimToNull(in.reason());
    Validators.requireLength(errors, "reason", reason, 0, 200, false);
    errors.throwIfAny();
    if (compensation.existsByEmployeeIdAndEffectiveDate(employeeId, in.effectiveDate())) {
      throw new ApiException(
          ErrorCode.DUPLICATE_EFFECTIVE_DATE, "A compensation record already exists for that date");
    }
    String normalized = new BigDecimal(amount).setScale(2).toPlainString();
    var saved =
        compensation.saveAndFlush(
            new CompensationRecord(
                employeeId,
                cipher.encrypt(normalized),
                in.currency(),
                in.payFrequency(),
                in.effectiveDate(),
                reason,
                actor.accountId()));
    accessLog.log(actor.accountId(), employeeId, "compensation", Action.CREATE);
    history.recordField(
        employeeId, "compensation", null, "(changed)", ChangeType.UPDATED, actor.accountId());
    return view(saved, accounts.findByIdIn(List.of(actor.accountId())).stream()
        .collect(Collectors.toMap(UserAccount::getId, Function.identity())));
  }

  private CompensationView view(CompensationRecord r, Map<UUID, UserAccount> creators) {
    UserAccount by = creators.get(r.getCreatedBy());
    String name =
        by == null ? "Unknown user" : by.getEmployee() != null ? by.getEmployee().getFullName() : by.getUsername();
    return new CompensationView(
        r.getId(),
        decrypt(r.getAmount()),
        r.getCurrency(),
        r.getPayFrequency(),
        r.getEffectiveDate(),
        r.getReason(),
        new Ref(r.getCreatedBy(), name),
        r.getCreatedAt());
  }

  private void requireEmployee(UUID employeeId) {
    if (!employees.existsById(employeeId)) {
      throw ApiException.notFound("Employee");
    }
  }

  private String decrypt(byte[] stored) {
    try {
      return cipher.decrypt(stored);
    } catch (FieldCipher.DecryptionException e) {
      throw new ApiException(
          ErrorCode.SENSITIVE_DATA_UNAVAILABLE,
          "Sensitive data cannot be read right now. Check the data encryption key configuration.");
    }
  }

  private Plain decryptAll(EmployeeSensitive r) {
    return new Plain(
        r.govIdType,
        decrypt(r.govIdNumber),
        decrypt(r.taxId),
        decrypt(r.bankAccountHolder),
        r.bankName,
        decrypt(r.bankAccountNumber),
        decrypt(r.bankRoutingCode));
  }

  private static Plain validate(SensitiveInput in) {
    var p =
        new Plain(
            Validators.trimToNull(in.govIdType()),
            Validators.trimToNull(in.govIdNumber()),
            Validators.trimToNull(in.taxId()),
            Validators.trimToNull(in.bankAccountHolder()),
            Validators.trimToNull(in.bankName()),
            strip(in.bankAccountNumber()),
            strip(in.bankRoutingCode()));
    var errors = new FieldErrors();
    if (in.version() == null) {
      errors.add("version", "is required");
    }
    Validators.requireLength(errors, "govIdType", p.govIdType, 0, 50, false);
    if (p.govIdNumber != null && p.govIdType == null) {
      errors.add("govIdType", "is required when a government ID number is given");
    }
    if (p.govIdNumber != null && !GOV_ID.matcher(p.govIdNumber).matches()) {
      errors.add("govIdNumber", "must be 4–30 letters, digits or dashes");
    }
    Validators.requireLength(errors, "taxId", p.taxId, 4, 30, false);
    Validators.requireLength(errors, "bankAccountHolder", p.bankAccountHolder, 0, 150, false);
    Validators.requireLength(errors, "bankName", p.bankName, 0, 150, false);
    Validators.requireLength(errors, "bankAccountNumber", p.bankAccountNumber, 4, 34, false);
    Validators.requireLength(errors, "bankRoutingCode", p.bankRoutingCode, 0, 20, false);
    errors.throwIfAny();
    return p;
  }

  private static String strip(String s) {
    String t = Validators.trimToNull(s);
    return t == null ? null : t.replace(" ", "");
  }

  private static String m(String plain) {
    return Masking.mask(Masking.last4(plain));
  }

  /** 0 means "nothing recorded yet"; a saved row is always 1 or more, so a stale 0 cannot overwrite it. */
  private static long clientVersion(EmployeeSensitive row) {
    return row == null ? 0 : row.getVersion() + 1;
  }

  private static SensitiveMasked masked(EmployeeSensitive r) {
    return new SensitiveMasked(
        clientVersion(r),
        r.govIdType,
        Masking.mask(r.govIdLast4),
        Masking.mask(r.taxIdLast4),
        Masking.maskPresent(r.bankAccountHolder != null),
        r.bankName,
        Masking.mask(r.bankAccountLast4),
        Masking.mask(r.bankRoutingLast4));
  }

  private static final SensitiveMasked EMPTY =
      new SensitiveMasked(0, null, null, null, null, null, null, null);

  private record Plain(
      String govIdType,
      String govIdNumber,
      String taxId,
      String bankAccountHolder,
      String bankName,
      String bankAccountNumber,
      String bankRoutingCode) {
    static final Plain EMPTY = new Plain(null, null, null, null, null, null, null);
  }
}
