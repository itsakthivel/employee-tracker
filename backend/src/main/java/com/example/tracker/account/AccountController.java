package com.example.tracker.account;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class AccountController {

  public record CreateRequest(UUID employeeId, Role role, String temporaryPassword) {}

  public record UpdateRequest(Role role, Boolean enabled) {}

  public record TemporaryPasswordRequest(String temporaryPassword) {}

  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @GetMapping
  public List<AccountService.AccountView> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Role role,
      @RequestParam(required = false) Boolean enabled) {
    return service.list(q, role, enabled);
  }

  @PostMapping
  public ResponseEntity<AccountService.AccountView> create(@RequestBody CreateRequest body) {
    var created = service.create(body.employeeId(), body.role(), body.temporaryPassword());
    return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  public AccountService.AccountView update(@PathVariable UUID id, @RequestBody UpdateRequest body) {
    return service.update(id, body.role(), body.enabled());
  }

  @PostMapping("/{id}/temporary-password")
  public ResponseEntity<Void> temporaryPassword(
      @PathVariable UUID id, @RequestBody TemporaryPasswordRequest body) {
    service.issueTemporaryPassword(id, body.temporaryPassword());
    return ResponseEntity.noContent().build();
  }
}
