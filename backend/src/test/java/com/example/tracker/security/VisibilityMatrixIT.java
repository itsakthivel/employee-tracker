package com.example.tracker.security;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SC-009: no sensitive or personal value reaches a non-administrator by any route. */
class VisibilityMatrixIT extends AbstractIntegrationTest {

  static final Set<String> PERSONAL_KEYS =
      Set.of("dateOfBirth", "homeAddress", "emergencyContact", "notes", "endDate", "employeeNumber",
          "status", "employmentType", "startDate", "isPreHire", "version");
  static final List<String> SECRETS =
      List.of("P12344821", "4821", "TAX-9090AB", "90AB", "GB29NWBK60161331926819", "6819",
          "NWBK600042", "0042", "First Bank", "Passport", "1 Secret Road", "Colleague notes", "99000");

  private Client admin;
  private Employee me;
  private Employee colleague;
  private Employee former;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    var dept = department("Engineering");
    me = withPersonal(employee("Me Myself", dept), "My notes");
    colleague = withPersonal(employee("Colleague Person", dept), "Colleague notes");
    colleague.setManager(me);
    colleague = employees.save(colleague);
    former = employee("Former Person", dept);
    former.setStatus(EmploymentStatus.TERMINATED);
    former.setEndDate(LocalDate.of(2024, 1, 1));
    former = employees.save(former);
    for (Employee e : List.of(me, colleague, former)) {
      admin.put("/api/v1/employees/" + e.getId() + "/sensitive", SENSITIVE).andExpect(status().isOk());
      admin.post("/api/v1/employees/" + e.getId() + "/compensation",
          "{\"amount\":\"99000\",\"currency\":\"GBP\",\"payFrequency\":\"ANNUAL\",\"effectiveDate\":\"2024-01-01\"}");
    }
    accountFor(me, Role.EMPLOYEE);
  }

  static final String SENSITIVE =
      "{\"version\":0,\"govIdType\":\"Passport\",\"govIdNumber\":\"P12344821\",\"taxId\":\"TAX-9090AB\","
          + "\"bankAccountHolder\":\"Holder Name\",\"bankName\":\"First Bank\","
          + "\"bankAccountNumber\":\"GB29NWBK60161331926819\",\"bankRoutingCode\":\"NWBK600042\"}";

  private Employee withPersonal(Employee e, String notes) {
    e.setDateOfBirth(LocalDate.of(1990, 1, 1));
    e.setHomeAddress("1 Secret Road");
    e.setEmergencyContactName("Next Of Kin");
    e.setEmergencyContactPhone("+447700900123");
    e.setNotes(notes);
    return employees.save(e);
  }

  @Test
  void employeeSeesOnlyDirectoryFieldsOfColleagues() throws Exception {
    Client c = signedIn(me.getWorkEmail());
    List<JsonNode> responses = new ArrayList<>();
    responses.add(body(c.get("/api/v1/employees").andExpect(status().isOk()).andExpect(matchesContract())));
    responses.add(body(c.get("/api/v1/employees?q=colleague").andExpect(status().isOk())));
    responses.add(body(c.get("/api/v1/employees/{id}", colleague.getId()).andExpect(status().isOk()).andExpect(matchesContract())));
    for (JsonNode r : responses) {
      assertNoKeys(r.has("items") ? r.path("items") : r, PERSONAL_KEYS);
      assertNoSecrets(r.toString());
    }
    assertThat(responses.get(2).path("view").asText()).isEqualTo("DIRECTORY");
  }

  @Test
  void employeeSeesOwnPersonalFieldsButNeverNotesOrSensitiveData() throws Exception {
    Client c = signedIn(me.getWorkEmail());
    for (var r : List.of(
        c.get("/api/v1/employees/{id}", me.getId()), c.get("/api/v1/me/profile"))) {
      JsonNode self = body(r.andExpect(status().isOk()).andExpect(matchesContract()));
      assertThat(self.path("view").asText()).isEqualTo("SELF");
      assertThat(self.path("homeAddress").asText()).isEqualTo("1 Secret Road");
      assertThat(self.has("notes")).isFalse();
      assertThat(self.toString()).doesNotContain("P12344821", "6819", "First Bank", "99000", "My notes");
    }
    JsonNode history = body(c.get("/api/v1/employees/{id}/history", me.getId()).andExpect(status().isOk()).andExpect(matchesContract()));
    assertNoSecrets(history.toString());
    assertThat(history.toString()).doesNotContain("bankAccountNumber", "compensation");
  }

  @Test
  void terminatedEmployeesAndOthersHistoryAreHidden() throws Exception {
    Client c = signedIn(me.getWorkEmail());
    c.get("/api/v1/employees/{id}", former.getId()).andExpect(status().isNotFound()).andExpect(matchesContract());
    c.get("/api/v1/employees/{id}/history", colleague.getId()).andExpect(status().isNotFound());
    c.get("/api/v1/employees?status=TERMINATED").andExpect(status().isBadRequest());
    JsonNode all = body(c.get("/api/v1/employees?includeTerminated=true&size=100"));
    assertThat(all.toString()).doesNotContain("Former Person");
  }

  @Test
  void employeeIsDeniedEveryAdminRoute() throws Exception {
    Client c = signedIn(me.getWorkEmail());
    for (String path : List.of(
        "/api/v1/employees/export", "/api/v1/reports/headcount", "/api/v1/users",
        "/api/v1/sensitive-access-log", "/api/v1/employees/" + me.getId() + "/sensitive",
        "/api/v1/employees/" + me.getId() + "/compensation")) {
      var res = c.get(path).andExpect(status().isForbidden()).andReturn().getResponse();
      assertNoSecrets(res.getContentAsString());
    }
  }

  @Test
  void adminSeesEverythingButStillOnlyMaskedSensitiveData() throws Exception {
    JsonNode profile = body(admin.get("/api/v1/employees/{id}", colleague.getId()).andExpect(status().isOk()).andExpect(matchesContract()));
    assertThat(profile.path("view").asText()).isEqualTo("ADMIN");
    assertThat(profile.path("notes").asText()).isEqualTo("Colleague notes");
    assertThat(profile.toString()).doesNotContain("P12344821", "GB29NWBK60161331926819");
    String csv = admin.get("/api/v1/employees/export?includeTerminated=true").andReturn().getResponse().getContentAsString();
    assertThat(csv).doesNotContain("P12344821", "TAX-9090AB", "GB29NWBK60161331926819", "First Bank", "99000");
  }

  private static void assertNoKeys(JsonNode node, Set<String> keys) {
    if (node.isArray()) {
      node.forEach(n -> assertNoKeys(n, keys));
      return;
    }
    if (node.isObject()) {
      node.fieldNames().forEachRemaining(k -> assertThat(keys).as("key %s", k).doesNotContain(k));
      node.elements().forEachRemaining(n -> assertNoKeys(n, Set.of()));
    }
  }

  private static void assertNoSecrets(String text) {
    for (String s : SECRETS) {
      assertThat(text).as("leaked %s", s).doesNotContain(s);
    }
  }
}
