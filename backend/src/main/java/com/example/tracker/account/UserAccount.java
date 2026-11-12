package com.example.tracker.account;

import com.example.tracker.common.BaseEntity;
import com.example.tracker.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

  @Column(nullable = false, length = 254)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id")
  private Employee employee;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected UserAccount() {}

  public UserAccount(String username, String passwordHash, Role role, Employee employee) {
    this.username = username.toLowerCase();
    this.passwordHash = passwordHash;
    this.role = role;
    this.employee = employee;
    this.passwordChangedAt = Instant.now();
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void setPassword(String hash, boolean mustChange) {
    this.passwordHash = hash;
    this.mustChangePassword = mustChange;
    this.passwordChangedAt = Instant.now();
    this.failedAttempts = 0;
    this.lockedUntil = null;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username.toLowerCase();
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public Employee getEmployee() {
    return employee;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public void setFailedAttempts(int failedAttempts) {
    this.failedAttempts = failedAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  @Override
  public String toString() {
    return "UserAccount[id=" + getId() + ", role=" + role + "]";
  }
}
