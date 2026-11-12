package com.example.tracker.lifecycle;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.employee.EmployeeViewPolicy;
import com.example.tracker.employee.dto.EmployeeView;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/employees/{id}")
public class LifecycleController {

  private final LifecycleService service;
  private final EmployeeViewPolicy policy;

  public LifecycleController(LifecycleService service, EmployeeViewPolicy policy) {
    this.service = service;
    this.policy = policy;
  }

  @PostMapping("/status")
  @Transactional
  public EmployeeView changeStatus(
      @PathVariable UUID id, @RequestBody LifecycleService.StatusChange body) {
    CurrentUser user = CurrentUser.get();
    return policy.viewFor(user, service.changeStatus(id, body, user));
  }

  @PostMapping("/reactivate")
  @Transactional
  public EmployeeView reactivate(
      @PathVariable UUID id, @RequestBody LifecycleService.Reactivation body) {
    CurrentUser user = CurrentUser.get();
    return policy.viewFor(user, service.reactivate(id, body, user));
  }
}
