package com.example.tracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracker.support.AbstractIntegrationTest;
import com.example.tracker.support.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Sensitive values and passwords never reach the logs, even on errors. */
@ExtendWith(OutputCaptureExtension.class)
class NoSensitiveLoggingIT extends AbstractIntegrationTest {

  @Test
  void sensitiveFlowsLogNoSecrets(CapturedOutput output) throws Exception {
    Client admin = admin();
    var ada = employee("Ada", department("Eng"));
    String url = "/api/v1/employees/" + ada.getId();
    admin.put(url + "/sensitive",
        "{\"version\":0,\"govIdType\":\"Passport\",\"govIdNumber\":\"P99887766\",\"taxId\":\"TAXSECRET1\","
            + "\"bankAccountNumber\":\"ACCT55443322\",\"bankRoutingCode\":\"ROUTE7788\"}")
        .andExpect(status().isOk());
    admin.post(url + "/sensitive/reveal", "{\"field\":\"bankAccountNumber\"}").andExpect(status().isOk());
    admin.post(url + "/compensation",
        "{\"amount\":\"123456.78\",\"currency\":\"GBP\",\"payFrequency\":\"ANNUAL\",\"effectiveDate\":\"2024-01-01\"}");
    admin.get(url + "/compensation");
    jdbc.update("update employee_sensitive set tax_id = ?", (Object) new byte[] {1, 1, 'x', 0, 0});
    admin.post(url + "/sensitive/reveal", "{\"field\":\"taxId\"}").andExpect(status().isInternalServerError());
    client().login("nobody@example.com", "My-Secret-Password-9");
    client().post("/api/v1/auth/password-reset", "{\"username\":\"" + ada.getWorkEmail() + "\"}");

    assertThat(output.getAll())
        .doesNotContain("P99887766", "TAXSECRET1", "ACCT55443322", "ROUTE7788", "123456.78", "My-Secret-Password-9");
  }
}
