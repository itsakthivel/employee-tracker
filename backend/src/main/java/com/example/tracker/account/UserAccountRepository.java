package com.example.tracker.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  @EntityGraph(attributePaths = "employee")
  Optional<UserAccount> findByUsernameIgnoreCase(String username);

  @EntityGraph(attributePaths = "employee")
  Optional<UserAccount> findWithEmployeeById(UUID id);

  Optional<UserAccount> findByEmployeeId(UUID employeeId);

  boolean existsByEmployeeId(UUID employeeId);

  boolean existsByUsernameIgnoreCase(String username);

  long countByRoleAndEnabledTrue(Role role);

  @EntityGraph(attributePaths = "employee")
  List<UserAccount> findAllByOrderByUsernameAsc();

  @EntityGraph(attributePaths = "employee")
  List<UserAccount> findByIdIn(java.util.Collection<UUID> ids);
}
