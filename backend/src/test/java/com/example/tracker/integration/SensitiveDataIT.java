package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SensitiveDataIT extends AbstractIntegrationTest {

  static final String FULL =
      "{\"version\":0,\"govIdType\":\"Passport\",\"govIdNumber\":\"P12344821\",\"taxId\":\"TAX-9090AB\","
          + "\"bankAccountHolder\":\"Ada Lovelace\",\"bankName\":\"First Bank\","
          + "\"bankAccountNumber\":\"GB29NWBK60161331926819\",\"bankRoutingCode\":\"NWBK600042\"}";

  private Client admin;
  private Employee ada;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    ada = employee("Ada Lovelace", department("Engineering"));
  }

  private String url(String suffix) {
    return "/api/v1/employees/" + ada.getId() + suffix;
  }

  @Test
  void valuesAreEncryptedAtRestAndShownMasked() throws Exception {
    admin.put(url("/sensitive"), FULL)
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.govIdNumber").value("••••4821"))
        .andExpect(jsonPath("$.bankAccountNumber").value("••••6819"))
        .andExpect(jsonPath("$.bankAccountHolder").value("••••"))
        .andExpect(jsonPath("$.bankName").value("First Bank"));

    var raw = jdbc.queryForMap("select gov_id_number, tax_id, bank_account_number, bank_account_holder, bank_routing_code from employee_sensitive");
    for (Object value : raw.values()) {
      String asText = new String((byte[]) value, StandardCharsets.ISO_8859_1);
      assertThat(asText).doesNotContain("P12344821", "TAX-9090AB", "GB29NWBK60161331926819", "Ada Lovelace", "NWBK600042");
    }
    admin.get(url("/sensitive")).andExpect(status().isOk()).andExpect(matchesContract())
        .andExpect(jsonPath("$.taxId").value("••••90AB"));
  }

  @Test
  void revealReturnsOneValueAndIsLogged() throws Exception {
    admin.put(url("/sensitive"), FULL).andExpect(status().isOk());
    long before = logCount();
    admin.post(url("/sensitive/reveal"), "{\"field\":\"bankAccountNumber\"}")
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.value").value("GB29NWBK60161331926819"));
    assertThat(logCount()).isEqualTo(before + 1);
    assertThat(jdbc.queryForObject("select action from sensitive_access_log order by occurred_at desc limit 1", String.class))
        .isEqualTo("REVEAL");
    admin.post(url("/sensitive/reveal"), "{\"field\":\"notes\"}").andExpect(status().isBadRequest());
  }

  @Test
  void everyChangedFieldIsLoggedAndHistoryIsMasked() throws Exception {
    admin.put(url("/sensitive"), FULL).andExpect(status().isOk());
    assertThat(logCount()).isEqualTo(7);
    assertThat(jdbc.queryForList("select distinct action from sensitive_access_log", String.class)).containsExactly("CREATE");

    String update = FULL.replace("\"version\":0", "\"version\":1").replace("GB29NWBK60161331926819", "12345678");
    admin.put(url("/sensitive"), update).andExpect(status().isOk());
    assertThat(logCount()).isEqualTo(8);

    var history = jdbc.queryForList("select field_name, old_value, new_value from employee_change where field_name = 'bankAccountNumber' order by changed_at");
    assertThat(history).hasSize(2);
    assertThat(history.get(1)).containsEntry("old_value", "••••6819").containsEntry("new_value", "••••5678");
    var allHistory = jdbc.queryForList("select coalesce(old_value,'') || coalesce(new_value,'') from employee_change", String.class);
    assertThat(String.join("|", allHistory)).doesNotContain("GB29NWBK60161331926819", "P12344821", "12345678", "Ada Lovelace");

    admin.put(url("/sensitive"), FULL).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
  }

  @Test
  void compensationIsADatedHistoryAndViewingItIsLogged() throws Exception {
    LocalDate today = LocalDate.now();
    admin.post(url("/compensation"), comp("70000", today.minusYears(1)))
        .andExpect(status().isCreated())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.amount").value("70000.00"));
    admin.post(url("/compensation"), comp("80000.5", today.minusDays(1))).andExpect(status().isCreated());
    admin.post(url("/compensation"), comp("90000", today.plusMonths(2))).andExpect(status().isCreated());
    admin.post(url("/compensation"), comp("1", today.minusDays(1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EFFECTIVE_DATE"));
    admin.post(url("/compensation"), comp("-5", today)).andExpect(status().isBadRequest());

    long before = logCount();
    admin.get(url("/compensation"))
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].amount").value("90000.00"))
        .andExpect(jsonPath("$.current.amount").value("80000.50"));
    assertThat(logCount()).isEqualTo(before + 1);
    var raw = jdbc.queryForList("select amount from compensation_record", byte[].class);
    assertThat(raw).allSatisfy(b -> assertThat(new String(b, StandardCharsets.ISO_8859_1)).doesNotContain("80000"));
  }

  @Test
  void accessLogIsAdminOnlyAndFilterable() throws Exception {
    admin.put(url("/sensitive"), FULL);
    admin.post(url("/sensitive/reveal"), "{\"field\":\"taxId\"}");
    admin.get("/api/v1/sensitive-access-log?employeeId=" + ada.getId())
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.totalItems").value(8))
        .andExpect(jsonPath("$.items[0].action").value("REVEAL"))
        .andExpect(jsonPath("$.items[0].employee.name").value("Ada Lovelace"));
  }

  @Test
  void employeesCannotReachAnySensitiveEndpoint() throws Exception {
    admin.put(url("/sensitive"), FULL);
    accountFor(ada, Role.EMPLOYEE);
    Client self = signedIn(ada.getWorkEmail());
    for (String path : List.of(url("/sensitive"), url("/compensation"), "/api/v1/sensitive-access-log")) {
      self.get(path).andExpect(status().isForbidden());
    }
    self.post(url("/sensitive/reveal"), "{\"field\":\"taxId\"}").andExpect(status().isForbidden());
    self.put(url("/sensitive"), FULL).andExpect(status().isForbidden());
    self.post(url("/compensation"), comp("1", LocalDate.now())).andExpect(status().isForbidden());
  }

  @Test
  void aMissingKeyBreaksRevealButNotTheMaskedView() throws Exception {
    admin.put(url("/sensitive"), FULL).andExpect(status().isOk());
    // Simulate a value encrypted under a key that is no longer configured.
    jdbc.update("update employee_sensitive set tax_id = ?", (Object) foreignCiphertext());
    admin.get(url("/sensitive")).andExpect(status().isOk()).andExpect(jsonPath("$.taxId").value("••••90AB"));
    long before = logCount();
    admin.post(url("/sensitive/reveal"), "{\"field\":\"taxId\"}")
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SENSITIVE_DATA_UNAVAILABLE"));
    assertThat(logCount()).isEqualTo(before);
    assertThat(Map.of()).isEmpty();
  }

  private static byte[] foreignCiphertext() {
    byte[] id = "gone".getBytes(StandardCharsets.US_ASCII);
    byte[] out = new byte[2 + id.length + 12 + 20];
    out[0] = 1;
    out[1] = (byte) id.length;
    System.arraycopy(id, 0, out, 2, id.length);
    return out;
  }

  private long logCount() {
    return jdbc.queryForObject("select count(*) from sensitive_access_log", Long.class);
  }

  private static String comp(String amount, LocalDate date) {
    return "{\"amount\":\"" + amount + "\",\"currency\":\"GBP\",\"payFrequency\":\"ANNUAL\",\"effectiveDate\":\"" + date + "\",\"reason\":\"Review\"}";
  }
}
