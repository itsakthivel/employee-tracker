package com.example.tracker.config;

import com.example.tracker.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Writes Problem Details from servlet filters, where controller advice does not apply. */
public final class ProblemWriter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProblemWriter() {}

  public static void write(HttpServletResponse response, ErrorCode code, String title, String detail)
      throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", title);
    body.put("status", code.status().value());
    body.put("detail", detail);
    body.put("code", code.name());
    response.setStatus(code.status().value());
    response.setContentType("application/problem+json");
    MAPPER.writeValue(response.getOutputStream(), body);
  }
}
