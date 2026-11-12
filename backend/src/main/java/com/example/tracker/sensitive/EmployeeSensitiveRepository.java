package com.example.tracker.sensitive;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmployeeSensitiveRepository extends JpaRepository<EmployeeSensitive, UUID> {
  Optional<EmployeeSensitive> findByEmployeeId(UUID employeeId);
}
