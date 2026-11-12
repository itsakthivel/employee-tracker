package com.example.tracker.account;

import com.example.tracker.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected PasswordResetToken() {}

  public PasswordResetToken(UserAccount user, String tokenHash, Instant expiresAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public UserAccount getUser() {
    return user;
  }

  public void markUsed(Instant now) {
    this.usedAt = now;
  }
}
