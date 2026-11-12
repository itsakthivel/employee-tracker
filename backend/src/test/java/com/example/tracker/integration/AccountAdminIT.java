package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AccountAdminIT extends AbstractIntegrationTest {

  @Test
  void adminCreatesSignInsWithTemporaryPasswords() throws Exception {
    Client admin = admin();
    Employee ada = employee("Ada", department("Eng"));
    var created = body(admin.post("/api/v1/users",
            "{\"employeeId\":\"" + ada.getId() + "\",\"role\":\"EMPLOYEE\",\"temporaryPassword\":\"Temporary-Pass-2026\"}")
        .andExpect(status().isCreated())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.username").value(ada.getWorkEmail()))
        .andExpect(jsonPath("$.mustChangePassword").value(true)));
    admin.post("/api/v1/users",
            "{\"employeeId\":\"" + ada.getId() + "\",\"role\":\"EMPLOYEE\",\"temporaryPassword\":\"Temporary-Pass-2026\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));
    admin.get("/api/v1/users").andExpect(status().isOk()).andExpect(matchesContract());

    Client adaClient = client();
    adaClient.login(ada.getWorkEmail(), "Temporary-Pass-2026").andExpect(jsonPath("$.mustChangePassword").value(true));
    adaClient.get("/api/v1/employees").andExpect(status().isForbidden());

    String id = created.path("id").asText();
    admin.post("/api/v1/users/" + id + "/temporary-password", "{\"temporaryPassword\":\"Another-Temp-2026\"}")
        .andExpect(status().isNoContent());
    adaClient.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }

  @Test
  void terminatedEmployeesCannotGetSignIns() throws Exception {
    Client admin = admin();
    Employee gone = employee("Gone", department("Eng"));
    gone.setStatus(EmploymentStatus.TERMINATED);
    gone.setEndDate(LocalDate.of(2024, 1, 1));
    employees.save(gone);
    admin.post("/api/v1/users",
            "{\"employeeId\":\"" + gone.getId() + "\",\"role\":\"EMPLOYEE\",\"temporaryPassword\":\"Temporary-Pass-2026\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMPLOYEE_TERMINATED"));
  }

  @Test
  void adminsCannotLockThemselvesOrTheOrganisationOut() throws Exception {
    var me = account("me@example.com", Role.ADMIN, null);
    Client admin = signedIn("me@example.com");
    admin.patch("/api/v1/users/" + me.getId(), "{\"enabled\":false}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    var other = account("other@example.com", Role.ADMIN, null);
    admin.patch("/api/v1/users/" + other.getId(), "{\"enabled\":false}").andExpect(status().isOk());
    Employee e = employee("Emp", department("Eng"));
    var emp = accountFor(e, Role.EMPLOYEE);
    Client empClient = signedIn(emp.getUsername());
    admin.patch("/api/v1/users/" + emp.getId(), "{\"role\":\"ADMIN\"}").andExpect(status().isOk());
    empClient.get("/api/v1/users").andExpect(status().isOk());
    admin.patch("/api/v1/users/" + emp.getId(), "{\"enabled\":false}").andExpect(status().isOk());
    empClient.get("/api/v1/auth/me").andExpect(status().isUnauthorized());
  }
}
