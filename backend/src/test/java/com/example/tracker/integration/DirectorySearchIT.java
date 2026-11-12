package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.reference.Department;
import com.example.tracker.reference.Location;
import com.example.tracker.reference.LocationRepository;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DirectorySearchIT extends AbstractIntegrationTest {

  @Autowired LocationRepository locations;

  private Client admin;
  private Client staff;
  private Department eng;
  private Department sales;
  private Location remote;

  @BeforeEach
  void setUp() throws Exception {
    admin = admin();
    eng = department("Engineering");
    sales = department("Sales");
    remote = locations.save(new Location("Remote"));
    for (int i = 0; i < 30; i++) {
      Employee e = employee("Engineer " + (char) ('A' + i % 26) + i, eng);
      if (i % 3 == 0) {
        e.setLocation(remote);
      }
      if (i == 5) {
        e.setStatus(EmploymentStatus.ON_LEAVE);
      }
      employees.save(e);
    }
    for (int i = 0; i < 25; i++) {
      employee("Seller " + i, sales);
    }
    Employee grace = employee("Grace Hopper", eng);
    grace.setEmployeeNumber("GH-0001");
    employees.save(grace);
    Employee gone = employee("Gone Person", eng);
    gone.setStatus(EmploymentStatus.TERMINATED);
    gone.setEndDate(LocalDate.of(2024, 6, 1));
    employees.save(gone);
    accountFor(grace, Role.EMPLOYEE);
    staff = signedIn(grace.getWorkEmail());
  }

  @Test
  void partialCaseInsensitiveSearchOnNameAndEmail() throws Exception {
    staff.get("/api/v1/employees?q=hOpPeR")
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].fullName").value("Grace Hopper"));
    staff.get("/api/v1/employees?q=seller").andExpect(jsonPath("$.totalItems").value(25));
    staff.get("/api/v1/employees?q=50%25").andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void employeeNumberSearchIsAdminOnly() throws Exception {
    admin.get("/api/v1/employees?q=gh-00").andExpect(jsonPath("$.totalItems").value(1));
    staff.get("/api/v1/employees?q=gh-00").andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void filtersCombineAndCountsAreCorrect() throws Exception {
    staff.get("/api/v1/employees?departmentId=" + eng.getId() + "&locationId=" + remote.getId())
        .andExpect(jsonPath("$.totalItems").value(10));
    staff.get("/api/v1/employees?departmentId=" + eng.getId() + "&status=ON_LEAVE")
        .andExpect(jsonPath("$.totalItems").value(1));
    staff.get("/api/v1/employees?departmentId=" + sales.getId() + "&departmentId=" + eng.getId())
        .andExpect(jsonPath("$.totalItems").value(56));
  }

  @Test
  void terminatedOnlyForAdminsWhoAskForThem() throws Exception {
    admin.get("/api/v1/employees?q=gone").andExpect(jsonPath("$.totalItems").value(0));
    admin.get("/api/v1/employees?q=gone&includeTerminated=true").andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].endDate").value("2024-06-01"));
    staff.get("/api/v1/employees?q=gone&includeTerminated=true").andExpect(jsonPath("$.totalItems").value(0));
    staff.get("/api/v1/employees?status=TERMINATED").andExpect(status().isBadRequest());
  }

  @Test
  void sortingAndPaging() throws Exception {
    var page = body(staff.get("/api/v1/employees?sort=-fullName").andExpect(status().isOk()));
    assertThat(page.path("size").asInt()).isEqualTo(25);
    assertThat(page.path("items").size()).isEqualTo(25);
    assertThat(page.path("totalPages").asInt()).isEqualTo(3);
    assertThat(page.path("items").get(0).path("fullName").asText()).isEqualTo("Seller 9");
    staff.get("/api/v1/employees?sort=department").andExpect(jsonPath("$.items[0].department.name").value("Engineering"));
    staff.get("/api/v1/employees?sort=startDate").andExpect(status().isBadRequest());
    staff.get("/api/v1/employees?sort=salary").andExpect(status().isBadRequest());
    admin.get("/api/v1/employees?sort=-status&size=100").andExpect(status().isOk());
    staff.get("/api/v1/employees?size=101").andExpect(status().isBadRequest());
    staff.get("/api/v1/employees?page=2").andExpect(jsonPath("$.items.length()").value(6));
  }

  @Test
  void emptyResultIsAnEmptyList() throws Exception {
    staff.get("/api/v1/employees?q=zzzz")
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalItems").value(0));
  }
}
