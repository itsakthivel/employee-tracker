package com.example.tracker.report;

import com.example.tracker.common.Ref;
import com.example.tracker.employee.EmploymentStatus;
import com.example.tracker.employee.EmploymentType;
import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-020: headcount of current (ACTIVE + ON_LEAVE) employees, plus a count per status. */
@Service
public class HeadcountService {

  public record DepartmentCount(Ref department, long count) {}

  public record Headcount(
      long totalActive,
      List<DepartmentCount> byDepartment,
      Map<String, Long> byStatus,
      Map<String, Long> byEmploymentType) {}

  private static final EnumSet<EmploymentStatus> CURRENT =
      EnumSet.of(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE);

  private final EntityManager em;

  public HeadcountService(EntityManager em) {
    this.em = em;
  }

  @Transactional(readOnly = true)
  public Headcount headcount() {
    List<DepartmentCount> byDepartment =
        em.createQuery(
                """
                select d.id, d.name, count(e.id) from Department d
                left join Employee e on e.department = d and e.status in :current
                group by d.id, d.name order by d.name
                """,
                Object[].class)
            .setParameter("current", CURRENT)
            .getResultList()
            .stream()
            .map(r -> new DepartmentCount(new Ref((UUID) r[0], (String) r[1]), (Long) r[2]))
            .toList();

    Map<String, Long> byStatus = new LinkedHashMap<>();
    for (EmploymentStatus s : EmploymentStatus.values()) {
      byStatus.put(s.name(), 0L);
    }
    em.createQuery("select e.status, count(e) from Employee e group by e.status", Object[].class)
        .getResultList()
        .forEach(r -> byStatus.put(((EmploymentStatus) r[0]).name(), (Long) r[1]));

    Map<String, Long> byType = new LinkedHashMap<>();
    for (EmploymentType t : EmploymentType.values()) {
      byType.put(t.name(), 0L);
    }
    em.createQuery(
            "select e.employmentType, count(e) from Employee e where e.status in :current group by e.employmentType",
            Object[].class)
        .setParameter("current", CURRENT)
        .getResultList()
        .forEach(r -> byType.put(((EmploymentType) r[0]).name(), (Long) r[1]));

    long total = byStatus.get("ACTIVE") + byStatus.get("ON_LEAVE");
    return new Headcount(total, byDepartment, byStatus, byType);
  }
}
