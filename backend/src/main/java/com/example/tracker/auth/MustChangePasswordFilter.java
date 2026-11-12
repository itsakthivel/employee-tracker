package com.example.tracker.auth;

import com.example.tracker.common.ErrorCode;
import com.example.tracker.config.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** FR-003c: until a temporary password is changed, only the password-change endpoints work. */
public class MustChangePasswordFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED =
      Set.of("/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (auth != null
        && auth.getPrincipal() instanceof CurrentUser user
        && user.mustChangePassword()
        && path.startsWith("/api/")
        && !ALLOWED.contains(path)) {
      ProblemWriter.write(
          response,
          ErrorCode.PASSWORD_CHANGE_REQUIRED,
          "Forbidden",
          "You must change your temporary password first");
      return;
    }
    chain.doFilter(request, response);
  }
}
