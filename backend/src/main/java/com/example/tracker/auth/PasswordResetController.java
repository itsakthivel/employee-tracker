package com.example.tracker.auth;

import com.example.tracker.account.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

  public record ResetRequest(String username) {}

  public record ResetConfirm(String token, String newPassword) {}

  private final PasswordResetService resets;

  public PasswordResetController(PasswordResetService resets) {
    this.resets = resets;
  }

  @PostMapping
  public ResponseEntity<Void> request(@RequestBody ResetRequest body) {
    resets.request(body.username());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@RequestBody ResetConfirm body) {
    resets.confirm(body.token(), body.newPassword());
    return ResponseEntity.noContent().build();
  }
}
