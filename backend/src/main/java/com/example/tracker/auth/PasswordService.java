package com.example.tracker.auth;

import com.example.tracker.account.SessionRevoker;
import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordService {

  private final UserAccountRepository accounts;
  private final PasswordEncoder encoder;
  private final PasswordPolicy policy;
  private final SessionRevoker revoker;

  public PasswordService(
      UserAccountRepository accounts,
      PasswordEncoder encoder,
      PasswordPolicy policy,
      SessionRevoker revoker) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.policy = policy;
    this.revoker = revoker;
  }

  /** Changes the signed-in user's password and ends their other sessions. */
  @Transactional
  public void change(
      CurrentUser user, String currentPassword, String newPassword, String keepSessionId) {
    var account =
        accounts.findById(user.accountId()).orElseThrow(() -> ApiException.notFound("Account"));
    if (currentPassword == null || !encoder.matches(currentPassword, account.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
    }
    policy.validate("newPassword", newPassword);
    account.setPassword(encoder.encode(newPassword), false);
    revoker.revokeAllExcept(account.getId(), keepSessionId);
  }
}
