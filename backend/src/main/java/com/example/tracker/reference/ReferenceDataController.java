package com.example.tracker.reference;

import com.example.tracker.auth.CurrentUser;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReferenceDataController {

  public record DepartmentInput(String name, UUID headEmployeeId) {}

  public record LocationInput(String name) {}

  private final ReferenceDataService service;

  public ReferenceDataController(ReferenceDataService service) {
    this.service = service;
  }

  @GetMapping("/departments")
  public List<ReferenceDataService.DepartmentView> departments() {
    return service.listDepartments(CurrentUser.get().isAdmin());
  }

  @PostMapping("/departments")
  public ResponseEntity<ReferenceDataService.DepartmentView> createDepartment(
      @RequestBody DepartmentInput body) {
    var created = service.createDepartment(body.name(), body.headEmployeeId());
    return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
  }

  @PutMapping("/departments/{id}")
  public ReferenceDataService.DepartmentView updateDepartment(
      @PathVariable UUID id, @RequestBody DepartmentInput body) {
    return service.updateDepartment(id, body.name(), body.headEmployeeId());
  }

  @DeleteMapping("/departments/{id}")
  public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
    service.deleteDepartment(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/locations")
  public List<ReferenceDataService.LocationView> locations() {
    return service.listLocations(CurrentUser.get().isAdmin());
  }

  @PostMapping("/locations")
  public ResponseEntity<ReferenceDataService.LocationView> createLocation(
      @RequestBody LocationInput body) {
    var created = service.createLocation(body.name());
    return ResponseEntity.created(URI.create("/api/v1/locations/" + created.id())).body(created);
  }

  @PutMapping("/locations/{id}")
  public ReferenceDataService.LocationView updateLocation(
      @PathVariable UUID id, @RequestBody LocationInput body) {
    return service.updateLocation(id, body.name());
  }

  @DeleteMapping("/locations/{id}")
  public ResponseEntity<Void> deleteLocation(@PathVariable UUID id) {
    service.deleteLocation(id);
    return ResponseEntity.noContent().build();
  }
}
