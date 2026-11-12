package com.example.tracker.sensitive;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.FieldErrors;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1")
public class SensitiveDataController {

  public record RevealRequest(String field) {}

  public record RevealResponse(String field, String value) {}

  private final SensitiveDataService service;
  private final AccessLogService accessLog;

  public SensitiveDataController(SensitiveDataService service, AccessLogService accessLog) {
    this.service = service;
    this.accessLog = accessLog;
  }

  @GetMapping("/employees/{id}/sensitive")
  public SensitiveDataService.SensitiveMasked get(@PathVariable UUID id) {
    return service.getMasked(id);
  }

  @PutMapping("/employees/{id}/sensitive")
  public SensitiveDataService.SensitiveMasked replace(
      @PathVariable UUID id, @RequestBody SensitiveDataService.SensitiveInput body) {
    return service.replace(id, body, CurrentUser.get());
  }

  @PostMapping("/employees/{id}/sensitive/reveal")
  public ResponseEntity<RevealResponse> reveal(
      @PathVariable UUID id, @RequestBody RevealRequest body) {
    String value = service.reveal(id, body.field(), CurrentUser.get());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new RevealResponse(body.field(), value));
  }

  @GetMapping("/employees/{id}/compensation")
  public ResponseEntity<SensitiveDataService.CompensationList> compensation(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.listCompensation(id, CurrentUser.get()));
  }

  @PostMapping("/employees/{id}/compensation")
  public ResponseEntity<SensitiveDataService.CompensationView> addCompensation(
      @PathVariable UUID id, @RequestBody SensitiveDataService.CompensationInput body) {
    var created = service.addCompensation(id, body, CurrentUser.get());
    return ResponseEntity.created(URI.create("/api/v1/employees/" + id + "/compensation"))
        .cacheControl(CacheControl.noStore())
        .body(created);
  }

  @GetMapping("/sensitive-access-log")
  public AccessLogService.AccessLogPage accessLog(
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    var errors = new FieldErrors();
    if (page < 0) {
      errors.add("page", "must be 0 or more");
    }
    if (size < 1 || size > 100) {
      errors.add("size", "must be between 1 and 100");
    }
    errors.throwIfAny();
    return accessLog.page(employeeId, actorId, from, to, page, size);
  }
}
