package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import com.example.tracker.reference.Department;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportsIT extends AbstractIntegrationTest {

  private Client admin;
  private Department eng;
  private Department sales;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    eng = department("Engineering");
    sales = department("Sales");
    department("Empty");
    for (int i = 0; i < 6; i++) {
      employee("Eng " + i, eng);
    }
    Employee leave = employee("Léa Dupont", eng);
    leave.setStatus(EmploymentStatus.ON_LEAVE);
    leave.setNotes("=HYPERLINK(\"http://evil\")");
    employees.save(leave);
    Employee contractor = employee("Sam Seller", sales);
    contractor.setEmploymentType(EmploymentType.CONTRACTOR);
    employees.save(contractor);
    Employee gone = employee("Gone", sales);
    gone.setStatus(EmploymentStatus.TERMINATED);
    gone.setEndDate(LocalDate.of(2024, 1, 1));
    employees.save(gone);
  }

  @Test
  void headcountMatchesTheList() throws Exception {
    admin.get("/api/v1/reports/headcount")
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.totalActive").value(8))
        .andExpect(jsonPath("$.byStatus.ACTIVE").value(7))
        .andExpect(jsonPath("$.byStatus.ON_LEAVE").value(1))
        .andExpect(jsonPath("$.byStatus.TERMINATED").value(1))
        .andExpect(jsonPath("$.byEmploymentType.CONTRACTOR").value(1))
        .andExpect(jsonPath("$.byEmploymentType.FULL_TIME").value(7))
        .andExpect(jsonPath("$.byDepartment[0].department.name").value("Empty"))
        .andExpect(jsonPath("$.byDepartment[0].count").value(0))
        .andExpect(jsonPath("$.byDepartment[1].count").value(7));
    admin.get("/api/v1/employees?departmentId=" + eng.getId()).andExpect(jsonPath("$.totalItems").value(7));
  }

  @Test
  void exportMatchesTheFilterAndIsSafeForSpreadsheets() throws Exception {
    var res =
        admin.get("/api/v1/employees/export?departmentId=" + eng.getId())
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"employees-" + LocalDate.now() + ".csv\""))
            .andReturn()
            .getResponse();
    byte[] bytes = res.getContentAsByteArray();
    assertThat(bytes[0]).isEqualTo((byte) 0xEF);
    String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    String[] lines = csv.split("\r\n");
    assertThat(lines).hasSize(8);
    assertThat(lines[0]).startsWith("Employee ID,Full name,").doesNotContainIgnoringCase("bank").doesNotContainIgnoringCase("salary");
    assertThat(csv).contains("Léa Dupont").contains("\"'=HYPERLINK(\"\"http://evil\"\")\"").doesNotContain("Sam Seller");
  }

  @Test
  void reportsAreAdminOnly() throws Exception {
    Employee e = employee("Staff", eng);
    accountFor(e, Role.EMPLOYEE);
    Client staff = signedIn(e.getWorkEmail());
    staff.get("/api/v1/reports/headcount").andExpect(status().isForbidden());
    staff.get("/api/v1/employees/export").andExpect(status().isForbidden());
  }
}
