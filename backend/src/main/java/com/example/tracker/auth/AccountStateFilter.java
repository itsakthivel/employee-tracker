package com.example.tracker.auth;

import com.example.tracker.account.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reloads the signed-in account on every request, so role changes, disabling and terminations take
 * effect immediately. A missing or disabled account ends the session.
 */
public class AccountStateFilter extends OncePerRequestFilter {

  private final UserAccountRepository accounts;

  public AccountStateFilter(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      var account = accounts.findWithEmployeeById(user.accountId());
      if (account.isEmpty() || !account.get().isEnabled()) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
      } else {
        SecurityContextHolder.setContext(contextFor(CurrentUser.of(account.get())));
      }
    }
    chain.doFilter(request, response);
  }

  public static SecurityContext contextFor(CurrentUser user) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))));
    return context;
  }
}
