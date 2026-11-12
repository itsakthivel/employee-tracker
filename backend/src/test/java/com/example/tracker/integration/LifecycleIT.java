package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.reference.Department;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecycleIT extends AbstractIntegrationTest {

  private Client admin;
  private Department eng;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    eng = department("Engineering");
  }

  private long version(UUID id) {
    return employees.findById(id).orElseThrow().getVersion();
  }

  private String statusBody(UUID id, String status, String extra) {
    return "{\"version\":" + version(id) + ",\"status\":\"" + status + "\"" + (extra == null ? "" : "," + extra) + "}";
  }

  @Test
  void leaveAndReturnAreRecorded() throws Exception {
    var e = employee("Ada", eng);
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "ON_LEAVE", null), e.getId())
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.status").value("ON_LEAVE"));
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "ACTIVE", null), e.getId())
        .andExpect(status().isOk());
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "ACTIVE", null), e.getId())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    assertThat(jdbc.queryForObject("select count(*) from employee_change where change_type = 'STATUS_CHANGED'", Long.class)).isEqualTo(2);
  }

  @Test
  void terminationNeedsAValidEndDate() throws Exception {
    var e = employee("Ada", eng);
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "TERMINATED", null), e.getId())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("endDate"));
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "TERMINATED", "\"endDate\":\"2020-01-01\""), e.getId())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("END_BEFORE_START"));
  }

  @Test
  void managerWithReportsNeedsAcknowledgement() throws Exception {
    var boss = employee("Boss", eng);
    var report = employee("Report", eng);
    report.setManager(boss);
    employees.save(report);
    admin.post("/api/v1/employees/{id}/status", statusBody(boss.getId(), "TERMINATED", "\"endDate\":\"2025-01-31\""), boss.getId())
        .andExpect(status().isConflict())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.code").value("DIRECT_REPORTS"))
        .andExpect(jsonPath("$.directReports[0].name").value("Report"));
    admin.post("/api/v1/employees/{id}/status",
            statusBody(boss.getId(), "TERMINATED", "\"endDate\":\"2025-01-31\",\"acknowledgeDirectReports\":true"), boss.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endDate").value("2025-01-31"));
    assertThat(employees.findById(report.getId()).orElseThrow().getManager().getId()).isEqualTo(boss.getId());
  }

  @Test
  void terminationEndsSignInImmediatelyAndReactivationForcesPasswordChange() throws Exception {
    Employee e = employee("Ada", eng);
    var adaAccount = accountFor(e, Role.EMPLOYEE);
    Client ada = signedIn(e.getWorkEmail());
    ada.get("/api/v1/auth/me").andExpect(status().isOk());

    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "TERMINATED", "\"endDate\":\"2025-01-31\""), e.getId())
        .andExpect(status().isOk());
    ada.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
    assertThat(jdbc.queryForObject(
            "select count(*) from spring_session where principal_name = ?", Long.class, adaAccount.getId().toString()))
        .isZero();
    client().login(e.getWorkEmail(), PASSWORD).andExpect(status().isUnauthorized());
    admin.get("/api/v1/employees?q=ada").andExpect(jsonPath("$.totalItems").value(0));

    admin.post("/api/v1/employees/{id}/reactivate", "{\"version\":" + version(e.getId()) + ",\"startDate\":\"2025-01-15\"}", e.getId())
        .andExpect(status().isUnprocessableEntity());
    admin.post("/api/v1/employees/{id}/reactivate",
            "{\"version\":" + version(e.getId()) + ",\"startDate\":\"2025-03-01\",\"jobTitle\":\"Staff Engineer\"}", e.getId())
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.endDate").doesNotExist())
        .andExpect(jsonPath("$.jobTitle").value("Staff Engineer"));
    assertThat(jdbc.queryForObject("select count(*) from employee_change where change_type = 'REACTIVATED'", Long.class)).isPositive();
    Client back = signedIn(e.getWorkEmail());
    back.get("/api/v1/auth/me").andExpect(jsonPath("$.mustChangePassword").value(true));
  }

  @Test
  void sessionsEndEvenAfterAnEmailChange() throws Exception {
    Employee e = employee("Ada", eng);
    accountFor(e, Role.EMPLOYEE);
    Client ada = signedIn(e.getWorkEmail());
    admin.patch("/api/v1/employees/{id}", "{\"version\":" + version(e.getId()) + ",\"workEmail\":\"renamed@example.com\"}", e.getId())
        .andExpect(status().isOk());
    admin.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "TERMINATED", "\"endDate\":\"2025-01-31\""), e.getId())
        .andExpect(status().isOk());
    ada.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void reactivateOnlyFromTerminatedAndLastAdminIsProtected() throws Exception {
    Employee e = employee("Ada", eng);
    admin.post("/api/v1/employees/{id}/reactivate", "{\"version\":" + version(e.getId()) + ",\"startDate\":\"2025-03-01\"}", e.getId())
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

    // Make Ada the only enabled admin, then try to terminate her record.
    accounts.findAll().forEach(a -> { a.setEnabled(false); accounts.save(a); });
    var adaAdmin = accountFor(e, Role.ADMIN);
    Client adaClient = signedIn(adaAdmin.getUsername());
    var other = employee("Other", eng);
    accountFor(other, Role.EMPLOYEE);
    adaClient.post("/api/v1/employees/{id}/status", statusBody(e.getId(), "TERMINATED", "\"endDate\":\"2025-01-31\""), e.getId())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
  }
}
