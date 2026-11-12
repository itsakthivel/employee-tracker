package com.example.tracker.seed;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds 5,000 employees across 20 departments for performance checks (profile "perf"). */
@Component
@Profile("perf")
@Order(2)
public class PerfSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(PerfSeedRunner.class);
  public static final int EMPLOYEES = 5000;

  private final JdbcTemplate jdbc;

  public PerfSeedRunner(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    Integer existing = jdbc.queryForObject("select count(*) from employee where employee_number like 'P-%'", Integer.class);
    if (existing != null && existing > 0) {
      return;
    }
    seed(jdbc);
    log.info("Perf seed: {} employees added", EMPLOYEES);
  }

  public static void seed(JdbcTemplate jdbc) {
    Timestamp now = Timestamp.from(Instant.now());
    List<UUID> depts = new ArrayList<>();
    for (int d = 0; d < 20; d++) {
      UUID id = UUID.randomUUID();
      depts.add(id);
      jdbc.update("insert into department (id, name, created_at, updated_at) values (?, ?, ?, ?)",
          id, "Perf Department " + d, now, now);
    }
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < EMPLOYEES; i++) {
      rows.add(new Object[] {
        UUID.randomUUID(), String.format("P-%05d", i), "Perf Person " + i, "perf" + i + "@example.com",
        "Specialist", depts.get(i % depts.size()), i % 9 == 0 ? "ON_LEAVE" : "ACTIVE",
        Date.valueOf(LocalDate.of(2015 + i % 10, 1 + i % 12, 1 + i % 27)), now, now
      });
    }
    jdbc.batchUpdate(
        "insert into employee (id, employee_number, full_name, work_email, job_title, department_id,"
            + " employment_type, status, start_date, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, 'FULL_TIME', ?, ?, ?, ?)",
        rows);
  }
}
