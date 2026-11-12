package com.example.tracker.employee;

import com.example.tracker.auth.CurrentUser;
import com.example.tracker.common.ApiException;
import com.example.tracker.employee.dto.EmployeeView;
import com.example.tracker.employee.dto.SelfUpdateRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** An employee's own record: always the self view, whatever their role. */
@RestController
@RequestMapping("/api/v1/me/profile")
public class SelfServiceController {

  private final EmployeeService service;
  private final EmployeeViewPolicy policy;

  public SelfServiceController(EmployeeService service, EmployeeViewPolicy policy) {
    this.service = service;
    this.policy = policy;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public EmployeeView get() {
    CurrentUser user = CurrentUser.get();
    if (user.employeeId() == null) {
      throw ApiException.notFound("Employee record");
    }
    return policy.selfView(service.get(user.employeeId()));
  }

  @PatchMapping
  @Transactional
  public EmployeeView update(@RequestBody SelfUpdateRequest body) {
    CurrentUser user = CurrentUser.get();
    return policy.selfView(service.updateSelf(body, user));
  }
}
