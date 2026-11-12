package com.example.tracker.auth;

import com.example.tracker.account.Role;
import com.example.tracker.account.UserAccount;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The signed-in user. Its name is the account id (not the username), so sessions stay findable
 * for revocation even after a work-email change.
 */
public record CurrentUser(
    UUID accountId, String username, Role role, UUID employeeId, boolean mustChangePassword)
    implements AuthenticatedPrincipal, Serializable {

  public static CurrentUser of(UserAccount account) {
    return new CurrentUser(
        account.getId(),
        account.getUsername(),
        account.getRole(),
        account.getEmployee() == null ? null : account.getEmployee().getId(),
        account.isMustChangePassword());
  }

  /** The current user; throws 401 if nobody is signed in. */
  public static CurrentUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
      return user;
    }
    throw new ApiException(ErrorCode.UNAUTHORIZED, "Not signed in");
  }

  @Override
  public String getName() {
    return accountId.toString();
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  public boolean isSelf(UUID otherEmployeeId) {
    return employeeId != null && employeeId.equals(otherEmployeeId);
  }
}
