package com.example.tracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.seed.PerfSeedRunner;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-003: search and filter results within 1 second at 5,000 employees. */
class SearchPerformanceIT extends AbstractIntegrationTest {

  @Test
  void searchAndFilterAreFastAtFiveThousandEmployees() throws Exception {
    PerfSeedRunner.seed(jdbc);
    jdbc.execute("analyze employee");
    UUID dept = jdbc.queryForObject("select id from department where name = 'Perf Department 3'", UUID.class);
    var me = employee("Searcher", departments.findById(dept).orElseThrow());
    accountFor(me, Role.EMPLOYEE);
    Client c = signedIn(me.getWorkEmail());
    c.get("/api/v1/employees?q=person 12").andExpect(status().isOk()); // warm up

    for (String query : new String[] {
        "/api/v1/employees?q=person 4321", "/api/v1/employees?q=perf&departmentId=" + dept,
        "/api/v1/employees?sort=department&page=150", "/api/v1/employees?q=example.com&sort=-fullName"}) {
      long start = System.nanoTime();
      c.get(query).andExpect(status().isOk());
      long ms = (System.nanoTime() - start) / 1_000_000;
      assertThat(ms).as(query).isLessThan(1000);
    }
  }
}
