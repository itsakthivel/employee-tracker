package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.reference.Department;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeeRecordsIT extends AbstractIntegrationTest {

  private Client admin;
  private Department eng;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    eng = department("Engineering");
  }

  @Test
  void createdRecordShowsEveryValueAndHistoryHasCreated() throws Exception {
    String extra =
        "\"phone\":\"+44 20 7946 0000\",\"dateOfBirth\":\"1990-05-01\",\"homeAddress\":\"1 High St\","
            + "\"emergencyContact\":{\"name\":\"Sam\",\"relationship\":\"Partner\",\"phone\":\"+447700900000\"},"
            + "\"notes\":\"Hired via referral\"";
    var created =
        body(
            admin
                .post("/api/v1/employees", employeeJson("E-100", "Ada Lovelace", "Ada@Example.com", eng.getId(), extra))
                .andExpect(status().isCreated())
                .andExpect(matchesContract())
                .andExpect(jsonPath("$.view").value("ADMIN"))
                .andExpect(jsonPath("$.workEmail").value("ada@example.com"))
                .andExpect(jsonPath("$.phone").value("+442079460000"))
                .andExpect(jsonPath("$.department.name").value("Engineering"))
                .andExpect(jsonPath("$.emergencyContact.name").value("Sam"))
                .andExpect(jsonPath("$.notes").value("Hired via referral"))
                .andExpect(jsonPath("$.isPreHire").value(false)));
    String id = created.path("id").asText();
    admin.get("/api/v1/employees/{id}", id).andExpect(status().isOk()).andExpect(matchesContract());
    admin
        .get("/api/v1/employees/{id}/history", id)
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.items[0].changeType").value("CREATED"))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void invalidFieldsAreReportedPerField() throws Exception {
    String bad =
        "{\"employeeNumber\":\"bad number!\",\"fullName\":\"\",\"workEmail\":\"not-an-email\","
            + "\"jobTitle\":\"x\",\"departmentId\":\"" + eng.getId() + "\",\"employmentType\":\"FULL_TIME\","
            + "\"status\":\"TERMINATED\",\"startDate\":\"2023-01-01\",\"dateOfBirth\":\"2015-01-01\"}";
    var problem =
        body(admin.post("/api/v1/employees", bad).andExpect(status().isBadRequest()).andExpect(matchesContract()));
    assertThat(problem.path("fieldErrors").findValuesAsText("field"))
        .contains("employeeNumber", "fullName", "workEmail", "status", "dateOfBirth");
    assertThat(employees.count()).isZero();
  }

  @Test
  void duplicateEmployeeNumberAndEmailAreRejected() throws Exception {
    admin.post("/api/v1/employees", employeeJson("E-100", "Ada", "ada@example.com", eng.getId(), ""))
        .andExpect(status().isCreated());
    admin.post("/api/v1/employees", employeeJson("e-100", "Bob", "bob@example.com", eng.getId(), ""))
        .andExpect(status().isConflict())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMPLOYEE_NUMBER"))
        .andExpect(jsonPath("$.detail").value("Employee ID already in use"));
    admin.post("/api/v1/employees", employeeJson("E-101", "Bob", "ADA@example.com", eng.getId(), ""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  void editingJobTitleWritesExactlyOneHistoryRow() throws Exception {
    var e = employee("Ada Lovelace", eng);
    admin
        .patch("/api/v1/employees/{id}", "{\"version\":" + e.getVersion() + ",\"jobTitle\":\"Lead Engineer\"}", e.getId())
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.jobTitle").value("Lead Engineer"));
    admin
        .get("/api/v1/employees/{id}/history", e.getId())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].changeType").value("UPDATED"))
        .andExpect(jsonPath("$.items[0].fieldName").value("jobTitle"))
        .andExpect(jsonPath("$.items[0].oldValue").value("Engineer"))
        .andExpect(jsonPath("$.items[0].newValue").value("Lead Engineer"))
        .andExpect(jsonPath("$.items[0].changedBy.name").exists());
  }

  @Test
  void nullClearsAnOptionalFieldAndUnknownFieldsAreRejected() throws Exception {
    var e = employee("Ada", eng);
    e.setPhone("+4412345678");
    e = employees.save(e);
    admin.patch("/api/v1/employees/{id}", "{\"version\":" + e.getVersion() + ",\"phone\":null}", e.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").doesNotExist());
    admin.patch("/api/v1/employees/{id}", "{\"version\":" + (e.getVersion() + 1) + ",\"status\":\"TERMINATED\"}", e.getId())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));
  }

  @Test
  void staleVersionIsRejected() throws Exception {
    var e = employee("Ada", eng);
    String body = "{\"version\":" + e.getVersion() + ",\"jobTitle\":\"A\"}";
    admin.patch("/api/v1/employees/{id}", body, e.getId()).andExpect(status().isOk());
    admin.patch("/api/v1/employees/{id}", body, e.getId())
        .andExpect(status().isConflict())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("STALE_VERSION"));
  }

  @Test
  void reportingCyclesAreRejected() throws Exception {
    var boss = employee("Boss", eng);
    var mid = employee("Mid", eng);
    var low = employee("Low", eng);
    mid.setManager(boss);
    low.setManager(mid);
    employees.save(mid);
    low = employees.save(low);
    boss = employees.findById(boss.getId()).orElseThrow();

    admin.patch("/api/v1/employees/{id}", "{\"version\":" + boss.getVersion() + ",\"managerId\":\"" + boss.getId() + "\"}", boss.getId())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("REPORTING_CYCLE"));
    admin.patch("/api/v1/employees/{id}", "{\"version\":" + boss.getVersion() + ",\"managerId\":\"" + low.getId() + "\"}", boss.getId())
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void futureStartDateIsAPreHire() throws Exception {
    admin.post(
            "/api/v1/employees",
            employeeJson("E-200", "Future", "future@example.com", eng.getId(), "")
                .replace("2023-03-01", java.time.LocalDate.now().plusMonths(1).toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.isPreHire").value(true));
  }

  @Test
  void emailChangeRenamesTheSignInAndKeepsTheirSession() throws Exception {
    var e = employee("Ada", eng);
    accountFor(e, Role.EMPLOYEE);
    Client ada = signedIn(e.getWorkEmail());
    admin.patch("/api/v1/employees/{id}", "{\"version\":" + e.getVersion() + ",\"workEmail\":\"ada.new@example.com\"}", e.getId())
        .andExpect(status().isOk());
    assertThat(accounts.findByEmployeeId(e.getId()).orElseThrow().getUsername()).isEqualTo("ada.new@example.com");
    ada.get("/api/v1/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.username").value("ada.new@example.com"));
    client().login("ada.new@example.com", PASSWORD).andExpect(status().isOk());
  }

  @Test
  void employeesCannotBeDeleted() throws Exception {
    var e = employee("Ada", eng);
    int code = admin.delete("/api/v1/employees/{id}", e.getId()).andReturn().getResponse().getStatus();
    assertThat(code).isGreaterThanOrEqualTo(400);
    assertThat(employees.existsById(e.getId())).isTrue();
  }

  @Test
  void departmentsAndLocationsCanBeManagedButNotDeletedWhileInUse() throws Exception {
    var created =
        body(admin.post("/api/v1/departments", "{\"name\":\"Sales\"}").andExpect(status().isCreated()).andExpect(matchesContract()));
    String salesId = created.path("id").asText();
    admin.put("/api/v1/departments/{id}", "{\"name\":\"Sales & Marketing\"}", salesId)
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.name").value("Sales & Marketing"));
    admin.post("/api/v1/departments", "{\"name\":\"sales & marketing\"}").andExpect(status().isConflict());
    admin.get("/api/v1/departments").andExpect(status().isOk()).andExpect(matchesContract());

    employee("Ada", eng);
    admin.delete("/api/v1/departments/{id}", eng.getId())
        .andExpect(status().isConflict())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("IN_USE"))
        .andExpect(jsonPath("$.employeeCount").value(1));
    admin.delete("/api/v1/departments/{id}", salesId).andExpect(status().isNoContent());

    var loc = body(admin.post("/api/v1/locations", "{\"name\":\"Remote\"}").andExpect(status().isCreated()).andExpect(matchesContract()));
    admin.put("/api/v1/locations/{id}", "{\"name\":\"Remote (any)\"}", loc.path("id").asText()).andExpect(status().isOk());
    admin.get("/api/v1/locations").andExpect(status().isOk()).andExpect(matchesContract());
    admin.delete("/api/v1/locations/{id}", loc.path("id").asText()).andExpect(status().isNoContent());
  }

  @Test
  void onlyAdminsCanCreateOrEdit() throws Exception {
    var e = employee("Ada", eng);
    accountFor(e, Role.EMPLOYEE);
    Client ada = signedIn(e.getWorkEmail());
    ada.post("/api/v1/employees", employeeJson("E-300", "X", "x@example.com", eng.getId(), ""))
        .andExpect(status().isForbidden());
    ada.patch("/api/v1/employees/{id}", "{\"version\":0,\"jobTitle\":\"CEO\"}", e.getId())
        .andExpect(status().isForbidden());
    ada.post("/api/v1/departments", "{\"name\":\"Mine\"}").andExpect(status().isForbidden());
    assertThat(UUID.randomUUID()).isNotNull();
  }
}
