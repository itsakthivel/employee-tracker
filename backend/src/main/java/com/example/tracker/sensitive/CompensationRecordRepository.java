package com.example.tracker.sensitive;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {
  List<CompensationRecord> findByEmployeeIdOrderByEffectiveDateDesc(UUID employeeId);

  boolean existsByEmployeeIdAndEffectiveDate(UUID employeeId, LocalDate effectiveDate);
}
