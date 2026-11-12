package com.example.tracker.seed;

import com.example.tracker.account.Role;
import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.auth.CurrentUser;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import com.example.tracker.history.ChangeRecorder;
import com.example.tracker.reference.Department;
import com.example.tracker.reference.DepartmentRepository;
import com.example.tracker.reference.Location;
import com.example.tracker.reference.LocationRepository;
import com.example.tracker.sensitive.CompensationRecord.PayFrequency;
import com.example.tracker.sensitive.SensitiveDataService;
import com.example.tracker.sensitive.SensitiveDataService.CompensationInput;
import com.example.tracker.sensitive.SensitiveDataService.SensitiveInput;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sample data for local development (profile "dev"). Skips itself if any employee exists. */
@Component
@Profile("dev")
@Order(1)
public class DevSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);
  public static final String PASSWORD = "ChangeMe-12345";

  private static final String[] FIRST = {
    "Alice", "Ben", "Chloe", "Dev", "Elena", "Farid", "Grace", "Hiro", "Isla", "Jonas", "Kavya",
    "Liam", "Maya", "Noah", "Olu", "Priya", "Quinn", "Rosa", "Sam", "Tara", "Uma", "Victor",
    "Wen", "Xavi", "Yara", "Zoe"
  };
  private static final String[] LAST = {
    "Anand", "Brown", "Chen", "Diaz", "Evans", "Fischer", "Garcia", "Haddad", "Ito", "Jensen",
    "Kumar", "Lopez", "Murphy", "Nakamura", "Okafor", "Patel", "Rossi", "Silva", "Tanaka", "Weber"
  };

  private final DepartmentRepository departments;
  private final LocationRepository locations;
  private final EmployeeRepository employees;
  private final UserAccountRepository accounts;
  private final PasswordEncoder encoder;
  private final ChangeRecorder history;
  private final SensitiveDataService sensitive;

  public DevSeedRunner(
      DepartmentRepository departments,
      LocationRepository locations,
      EmployeeRepository employees,
      UserAccountRepository accounts,
      PasswordEncoder encoder,
      ChangeRecorder history,
      SensitiveDataService sensitive) {
    this.departments = departments;
    this.locations = locations;
    this.employees = employees;
    this.accounts = accounts;
    this.encoder = encoder;
    this.history = history;
    this.sensitive = sensitive;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (employees.count() > 0) {
      return;
    }
    String hash = encoder.encode(PASSWORD);
    UserAccount admin = accounts.save(new UserAccount("admin@example.com", hash, Role.ADMIN, null));

    List<Department> depts = new ArrayList<>();
    for (String name : List.of("Engineering", "Sales", "Finance", "People")) {
      depts.add(departments.save(new Department(name)));
    }
    List<Location> locs = new ArrayList<>();
    for (String name : List.of("Head Office", "Branch Office", "Remote")) {
      locs.add(locations.save(new Location(name)));
    }

    List<Employee> all = new ArrayList<>();
    // Department heads report to nobody; leads report to heads; the rest report to leads.
    for (int i = 0; i < 60; i++) {
      Department dept = depts.get(i % depts.size());
      String first = FIRST[i % FIRST.length];
      String last = LAST[(i * 7) % LAST.length];
      Employee e = new Employee();
      e.setEmployeeNumber(String.format("E-%04d", 1001 + i));
      e.setFullName(first + " " + last);
      e.setWorkEmail(
          switch (i) {
            case 4 -> "alice@example.com";
            case 30 -> "newhire@example.com";
            case 52 -> "former@example.com";
            default -> (first + "." + last + i).toLowerCase() + "@example.com";
          });
      if (i == 4) {
        e.setFullName("Alice Morgan");
      }
      e.setPhone(String.format("+4477009%05d", i));
      e.setJobTitle(i < 4 ? "Head of " + dept.getName() : i < 12 ? "Team Lead" : "Specialist");
      e.setDepartment(dept);
      e.setLocation(locs.get(i % locs.size()));
      e.setEmploymentType(i % 10 == 9 ? EmploymentType.CONTRACTOR : i % 13 == 12 ? EmploymentType.PART_TIME : EmploymentType.FULL_TIME);
      e.setStatus(EmploymentStatus.ACTIVE);
      e.setStartDate(LocalDate.of(2018 + i % 7, 1 + i % 12, 1 + i % 27));
      e.setDateOfBirth(LocalDate.of(1975 + i % 25, 1 + i % 12, 1 + i % 27));
      e.setHomeAddress((10 + i) + " Example Street, Sample Town");
      e.setEmergencyContactName("Contact of " + first);
      e.setEmergencyContactRelationship("Partner");
      e.setEmergencyContactPhone(String.format("+4477008%05d", i));
      if (i >= 4) {
        e.setManager(all.get(i < 12 ? i % 4 : 4 + (i % 8)));
      }
      all.add(employees.save(e));
    }
    // Some on leave, some terminated, one pre-hire.
    for (int i : new int[] {20, 33, 47}) {
      all.get(i).setStatus(EmploymentStatus.ON_LEAVE);
    }
    for (int i : new int[] {52, 55, 57, 59}) {
      Employee e = all.get(i);
      e.setStatus(EmploymentStatus.TERMINATED);
      e.setEndDate(e.getStartDate().plusYears(1));
    }
    all.get(58).setStartDate(LocalDate.now().plusMonths(1));
    employees.flush();
    all.forEach(e -> history.recordCreated(e.getId(), admin.getId()));

    accounts.save(new UserAccount("alice@example.com", hash, Role.EMPLOYEE, all.get(4)));
    UserAccount newhire = new UserAccount(all.get(30).getWorkEmail(), hash, Role.EMPLOYEE, all.get(30));
    newhire.setMustChangePassword(true);
    accounts.save(newhire);
    UserAccount former = new UserAccount(all.get(52).getWorkEmail(), hash, Role.EMPLOYEE, all.get(52));
    former.setEnabled(false);
    accounts.save(former);

    CurrentUser actor = CurrentUser.of(admin);
    for (int i : new int[] {4, 5, 6, 13, 21}) {
      Employee e = all.get(i);
      sensitive.replace(
          e.getId(),
          new SensitiveInput(0L, "Passport", "P" + (55500000 + i), "TAX-" + (900000 + i),
              e.getFullName(), "Example Bank", "GB29NWBK601613319268" + String.format("%02d", i),
              "NWBK60" + String.format("%04d", i)),
          actor);
      sensitive.addCompensation(e.getId(),
          new CompensationInput(String.valueOf(52000 + i * 1000), "GBP", PayFrequency.ANNUAL,
              e.getStartDate(), "Starting salary"), actor);
      sensitive.addCompensation(e.getId(),
          new CompensationInput(String.valueOf(58000 + i * 1000), "GBP", PayFrequency.ANNUAL,
              LocalDate.now().minusMonths(6), "Annual review"), actor);
    }
    log.info(
        "Dev seed: 60 employees; sign in as admin@example.com, alice@example.com, {} (must change"
            + " password) or {} (disabled), password {}",
        newhire.getUsername(), former.getUsername(), PASSWORD);
  }
}
