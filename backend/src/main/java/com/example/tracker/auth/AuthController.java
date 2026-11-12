package com.example.tracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  public record LoginRequest(String username, String password) {}

  public record ChangePasswordRequest(String currentPassword, String newPassword) {}

  public record CurrentUserResponse(
      UUID id, String username, String role, UUID employeeId, boolean mustChangePassword) {
    static CurrentUserResponse of(CurrentUser u) {
      return new CurrentUserResponse(
          u.accountId(), u.username(), u.role().name(), u.employeeId(), u.mustChangePassword());
    }
  }

  private final LoginService loginService;
  private final PasswordService passwordService;
  private final SecurityContextRepository contextRepository;

  public AuthController(
      LoginService loginService,
      PasswordService passwordService,
      SecurityContextRepository contextRepository) {
    this.loginService = loginService;
    this.passwordService = passwordService;
    this.contextRepository = contextRepository;
  }

  @PostMapping("/login")
  public CurrentUserResponse login(
      @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
    var account = loginService.authenticate(body.username(), body.password());
    CurrentUser user = CurrentUser.of(account);
    // Session fixation protection: never reuse a pre-login session id.
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    SecurityContext context = AccountStateFilter.contextFor(user);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    return CurrentUserResponse.of(user);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public CurrentUserResponse me() {
    return CurrentUserResponse.of(CurrentUser.get());
  }

  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    passwordService.change(
        CurrentUser.get(),
        body.currentPassword(),
        body.newPassword(),
        session == null ? null : session.getId());
    return ResponseEntity.noContent().build();
  }
}
