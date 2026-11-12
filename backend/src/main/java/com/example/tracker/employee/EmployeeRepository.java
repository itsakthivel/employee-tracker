package com.example.tracker.employee;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository
    extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

  @Override
  @EntityGraph(attributePaths = {"department", "location", "manager"})
  Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"department", "location", "manager"})
  List<Employee> findAll(Specification<Employee> spec, org.springframework.data.domain.Sort sort);

  boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

  boolean existsByEmployeeNumberIgnoreCaseAndIdNot(String employeeNumber, UUID id);

  boolean existsByWorkEmailIgnoreCase(String workEmail);

  boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, UUID id);

  List<Employee> findByManagerIdOrderByFullNameAsc(UUID managerId);

  long countByDepartmentId(UUID departmentId);

  long countByLocationId(UUID locationId);

  /** True if {@code candidateId} is {@code rootId} or anywhere below it in the reporting tree. */
  @Query(
      value =
          """
          with recursive subtree(id) as (
            select id from employee where id = :rootId
            union
            select e.id from employee e join subtree s on e.manager_id = s.id
          )
          select exists (select 1 from subtree where id = :candidateId)
          """,
      nativeQuery = true)
  boolean isInSubtree(@Param("rootId") UUID rootId, @Param("candidateId") UUID candidateId);
}
