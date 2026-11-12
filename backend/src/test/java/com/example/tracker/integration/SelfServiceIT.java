package com.example.tracker.integration;

import static com.example.tracker.support.ContractAssert.matchesContract;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.account.Role;
import com.example.tracker.employee.Employee;
import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfServiceIT extends AbstractIntegrationTest {

  private Employee alice;
  private Client client;

  @BeforeEach
  void setUp() throws Exception {
    alice = employee("Alice Example", department("Engineering"));
    accountFor(alice, Role.EMPLOYEE);
    client = signedIn(alice.getWorkEmail());
  }

  @Test
  void canUpdateOwnPhoneAndEmergencyContact() throws Exception {
    client.patch("/api/v1/me/profile",
            "{\"version\":" + alice.getVersion() + ",\"phone\":\"+44 7700 900111\","
                + "\"emergencyContact\":{\"name\":\"Bob\",\"relationship\":\"Brother\",\"phone\":\"+447700900222\"}}")
        .andExpect(status().isOk())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.view").value("SELF"))
        .andExpect(jsonPath("$.phone").value("+447700900111"))
        .andExpect(jsonPath("$.emergencyContact.name").value("Bob"));
    client.get("/api/v1/employees/{id}/history", alice.getId())
        .andExpect(jsonPath("$.items[0].changeType").value("SELF_UPDATED"))
        .andExpect(jsonPath("$.totalItems").value(4));
  }

  @Test
  void otherFieldsAreRejected() throws Exception {
    client.patch("/api/v1/me/profile", "{\"version\":" + alice.getVersion() + ",\"jobTitle\":\"CEO\"}")
        .andExpect(status().isBadRequest())
        .andExpect(matchesContract())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("jobTitle"));
    client.patch("/api/v1/employees/{id}", "{\"version\":" + alice.getVersion() + ",\"phone\":\"+447700900111\"}", alice.getId())
        .andExpect(status().isForbidden());
  }

  @Test
  void staleVersionAndBadPhoneAreRejected() throws Exception {
    client.patch("/api/v1/me/profile", "{\"version\":99,\"phone\":\"+447700900111\"}")
        .andExpect(status().isConflict());
    client.patch("/api/v1/me/profile", "{\"version\":" + alice.getVersion() + ",\"phone\":\"12\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));
  }

  @Test
  void adminWithoutEmployeeRecordHasNoProfile() throws Exception {
    admin().get("/api/v1/me/profile").andExpect(status().isNotFound()).andExpect(matchesContract());
  }
}
