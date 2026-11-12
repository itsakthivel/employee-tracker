package com.example.tracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Spring Security's recommended handler for single-page apps: the SPA echoes the raw cookie value
 * in the X-XSRF-TOKEN header, while server-rendered values keep BREACH protection.
 */
final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    delegate.handle(request, response, csrfToken);
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
      return super.resolveCsrfTokenValue(request, csrfToken);
    }
    return delegate.resolveCsrfTokenValue(request, csrfToken);
  }
}
