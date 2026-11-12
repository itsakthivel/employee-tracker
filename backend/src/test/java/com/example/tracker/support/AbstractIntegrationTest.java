package com.example.tracker.support;

import com.example.tracker.account.Role;
import com.example.tracker.account.UserAccount;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import com.example.tracker.reference.Department;
import com.example.tracker.reference.DepartmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

  public static final String PASSWORD = "Correct-Horse-Battery-9";
  /** A fixed 32-byte test key (base64). */
  public static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit").withExposedPorts(1025, 8025);

  static {
    POSTGRES.start();
    MAILPIT.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.mail.host", MAILPIT::getHost);
    r.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    r.add("tracker.data-key.active-id", () -> "test1");
    r.add("TRACKER_DATA_KEY_test1", () -> TEST_KEY);
    r.add("tracker.app-base-url", () -> "http://localhost:5173");
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;
  @Autowired protected UserAccountRepository accounts;
  @Autowired protected EmployeeRepository employees;
  @Autowired protected DepartmentRepository departments;
  @Autowired protected PasswordEncoder encoder;
  @Autowired protected Clock clock;

  protected final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void resetState() throws Exception {
    List<String> tables =
        jdbc.queryForList(
            "select tablename from pg_tables where schemaname = 'public'"
                + " and tablename <> 'flyway_schema_history'",
            String.class);
    jdbc.execute("truncate table " + String.join(", ", tables) + " cascade");
    ((MutableClock) clock).reset();
    http.send(
        HttpRequest.newBuilder(mailpit("/api/v1/messages")).DELETE().build(),
        HttpResponse.BodyHandlers.discarding());
  }

  protected Client client() {
    return new Client(mvc);
  }

  protected MutableClock clock() {
    return (MutableClock) clock;
  }

  protected Department department(String name) {
    return departments.save(new Department(name));
  }

  protected Employee employee(String name, Department dept) {
    var e = new Employee();
    String slug = name.toLowerCase().replaceAll("[^a-z]", "") + UUID.randomUUID().toString().substring(0, 6);
    e.setEmployeeNumber("E-" + slug.substring(slug.length() - 6).toUpperCase());
    e.setFullName(name);
    e.setWorkEmail(slug + "@example.com");
    e.setJobTitle("Engineer");
    e.setDepartment(dept);
    e.setEmploymentType(EmploymentType.FULL_TIME);
    e.setStatus(EmploymentStatus.ACTIVE);
    e.setStartDate(LocalDate.of(2022, 1, 10));
    return employees.save(e);
  }

  protected UserAccount account(String username, Role role, Employee employee) {
    return accounts.save(new UserAccount(username, encoder.encode(PASSWORD), role, employee));
  }

  protected UserAccount accountFor(Employee employee, Role role) {
    return account(employee.getWorkEmail(), role, employee);
  }

  protected Client signedIn(String username) throws Exception {
    Client c = client();
    c.login(username, PASSWORD).andExpect(r -> {
      if (r.getResponse().getStatus() != 200) {
        throw new AssertionError("login failed: " + r.getResponse().getContentAsString());
      }
    });
    return c;
  }

  /** Signs in as a fresh administrator with no employee record. */
  protected Client admin() throws Exception {
    String username = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    account(username, Role.ADMIN, null);
    return signedIn(username);
  }

  protected JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  /** A valid create-employee body; override fields with {@code extra} (JSON members). */
  protected static String employeeJson(String number, String name, String email, UUID deptId, String extra) {
    return "{\"employeeNumber\":\"" + number + "\",\"fullName\":\"" + name
        + "\",\"workEmail\":\"" + email + "\",\"jobTitle\":\"Engineer\",\"departmentId\":\""
        + deptId + "\",\"employmentType\":\"FULL_TIME\",\"status\":\"ACTIVE\","
        + "\"startDate\":\"2023-03-01\"" + (extra == null || extra.isEmpty() ? "" : "," + extra) + "}";
  }

  protected URI mailpit(String path) {
    return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
  }

  protected JsonNode mailMessages() throws Exception {
    var res =
        http.send(
            HttpRequest.newBuilder(mailpit("/api/v1/messages")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    return json.readTree(res.body()).path("messages");
  }

  protected String mailText(String id) throws Exception {
    var res =
        http.send(
            HttpRequest.newBuilder(mailpit("/api/v1/message/" + id)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    return json.readTree(res.body()).path("Text").asText();
  }
}
