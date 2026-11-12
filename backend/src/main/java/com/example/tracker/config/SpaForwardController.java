package com.example.tracker.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the single-page app for client-side routes such as /employees/123 or /me. */
@Controller
public class SpaForwardController {
  @GetMapping({
    "/",
    "/sign-in",
    "/change-password",
    "/forgot-password",
    "/reset-password",
    "/me",
    "/employees/**",
    "/overview",
    "/departments",
    "/locations",
    "/users",
    "/access-log"
  })
  public String index() {
    return "forward:/index.html";
  }
}
