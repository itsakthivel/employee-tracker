package com.example.tracker.history;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeChangeRepository extends JpaRepository<EmployeeChange, UUID> {
  Page<EmployeeChange> findByEmployeeIdOrderByChangedAtDesc(UUID employeeId, Pageable pageable);

  /** History limited to the given fields; CREATED rows (no field) are always included. */
  @org.springframework.data.jpa.repository.Query(
      """
      select c from EmployeeChange c
      where c.employeeId = :employeeId and (c.fieldName is null or c.fieldName in :fields)
      order by c.changedAt desc
      """)
  Page<EmployeeChange> findVisible(
      @org.springframework.data.repository.query.Param("employeeId") UUID employeeId,
      @org.springframework.data.repository.query.Param("fields") Collection<String> fields,
      Pageable pageable);

  long countByEmployeeId(UUID employeeId);
}
