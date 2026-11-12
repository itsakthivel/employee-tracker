package com.example.tracker.reference;

import com.example.tracker.common.ApiException;
import com.example.tracker.common.ErrorCode;
import com.example.tracker.common.FieldErrors;
import com.example.tracker.common.Ref;
import com.example.tracker.common.Validators;
import com.example.tracker.employee.Employee;
import com.example.tracker.employee.EmployeeRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-022/FR-023: departments and locations. */
@Service
public class ReferenceDataService {

  public record DepartmentView(UUID id, String name, Ref head, Long employeeCount) {}

  public record LocationView(UUID id, String name, Long employeeCount) {}

  private final DepartmentRepository departments;
  private final LocationRepository locations;
  private final EmployeeRepository employees;

  public ReferenceDataService(
      DepartmentRepository departments, LocationRepository locations, EmployeeRepository employees) {
    this.departments = departments;
    this.locations = locations;
    this.employees = employees;
  }

  @Transactional(readOnly = true)
  public List<DepartmentView> listDepartments(boolean withCounts) {
    return departments.findAllByOrderByNameAsc().stream().map(d -> view(d, withCounts)).toList();
  }

  @Transactional
  public DepartmentView createDepartment(String name, UUID headId) {
    String clean = validName(name);
    if (departments.existsByNameIgnoreCase(clean)) {
      throw new ApiException(ErrorCode.DUPLICATE_NAME, "A department with that name already exists");
    }
    var d = new Department(clean);
    d.setHead(head(headId));
    return view(departments.save(d), true);
  }

  @Transactional
  public DepartmentView updateDepartment(UUID id, String name, UUID headId) {
    var d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
    String clean = validName(name);
    if (departments.existsByNameIgnoreCaseAndIdNot(clean, id)) {
      throw new ApiException(ErrorCode.DUPLICATE_NAME, "A department with that name already exists");
    }
    d.setName(clean);
    d.setHead(head(headId));
    return view(d, true);
  }

  @Transactional
  public void deleteDepartment(UUID id) {
    var d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
    long count = employees.countByDepartmentId(id);
    if (count > 0) {
      throw inUse("department", count);
    }
    departments.delete(d);
  }

  @Transactional(readOnly = true)
  public List<LocationView> listLocations(boolean withCounts) {
    return locations.findAllByOrderByNameAsc().stream().map(l -> view(l, withCounts)).toList();
  }

  @Transactional
  public LocationView createLocation(String name) {
    String clean = validName(name);
    if (locations.existsByNameIgnoreCase(clean)) {
      throw new ApiException(ErrorCode.DUPLICATE_NAME, "A location with that name already exists");
    }
    return view(locations.save(new Location(clean)), true);
  }

  @Transactional
  public LocationView updateLocation(UUID id, String name) {
    var l = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
    String clean = validName(name);
    if (locations.existsByNameIgnoreCaseAndIdNot(clean, id)) {
      throw new ApiException(ErrorCode.DUPLICATE_NAME, "A location with that name already exists");
    }
    l.setName(clean);
    return view(l, true);
  }

  @Transactional
  public void deleteLocation(UUID id) {
    var l = locations.findById(id).orElseThrow(() -> ApiException.notFound("Location"));
    long count = employees.countByLocationId(id);
    if (count > 0) {
      throw inUse("location", count);
    }
    locations.delete(l);
  }

  private static ApiException inUse(String what, long count) {
    return new ApiException(
        ErrorCode.IN_USE,
        count + " employee(s) are still assigned to this " + what,
        Map.of("employeeCount", count));
  }

  private static String validName(String name) {
    String clean = Validators.trimToNull(name);
    var errors = new FieldErrors();
    Validators.requireLength(errors, "name", clean, 1, 100, true);
    errors.throwIfAny();
    return clean;
  }

  private Employee head(UUID headId) {
    if (headId == null) {
      return null;
    }
    Employee e = employees.findById(headId).orElse(null);
    if (e == null || e.isTerminated()) {
      new FieldErrors().add("headEmployeeId", "must be a current employee").throwIfAny();
    }
    return e;
  }

  private DepartmentView view(Department d, boolean withCounts) {
    Employee h = d.getHead();
    return new DepartmentView(
        d.getId(),
        d.getName(),
        h == null ? null : new Ref(h.getId(), h.getFullName()),
        withCounts ? employees.countByDepartmentId(d.getId()) : null);
  }

  private LocationView view(Location l, boolean withCounts) {
    return new LocationView(
        l.getId(), l.getName(), withCounts ? employees.countByLocationId(l.getId()) : null);
  }
}
