package com.example.tracker.config;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import com.example.tracker.account.UserAccountRepository;
import com.example.tracker.auth.AccountStateFilter;
import com.example.tracker.auth.MustChangePasswordFilter;
import com.example.tracker.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

  private static final String API = "/api/v1";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      UserAccountRepository accounts,
      SecurityContextRepository contextRepository,
      @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies)
      throws Exception {
    http.securityContext(c -> c.securityContextRepository(contextRepository))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(
            c ->
                c.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .addFilterBefore(new AccountStateFilter(accounts), AnonymousAuthenticationFilter.class)
        .addFilterAfter(new MustChangePasswordFilter(), AccountStateFilter.class)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(
            h -> {
              h.contentSecurityPolicy(
                  csp ->
                      csp.policyDirectives(
                          "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;"
                              + " frame-ancestors 'none'"));
              h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
              if (!secureCookies) {
                h.httpStrictTransportSecurity(hsts -> hsts.disable());
              }
            })
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            ProblemWriter.write(
                                res, ErrorCode.UNAUTHORIZED, "Unauthorized", "Sign in to continue"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            ProblemWriter.write(
                                res,
                                ErrorCode.FORBIDDEN,
                                "Forbidden",
                                "You do not have access to this")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        POST,
                        API + "/auth/login",
                        API + "/auth/password-reset",
                        API + "/auth/password-reset/confirm")
                    .permitAll()
                    // Admin-only rules, listed as explicit (method, path) pairs.
                    .requestMatchers(POST, API + "/employees")
                    .hasRole("ADMIN")
                    .requestMatchers(PATCH, API + "/employees/*")
                    .hasRole("ADMIN")
                    .requestMatchers(POST, API + "/employees/*/status", API + "/employees/*/reactivate")
                    .hasRole("ADMIN")
                    .requestMatchers(API + "/employees/*/sensitive", API + "/employees/*/sensitive/**")
                    .hasRole("ADMIN")
                    .requestMatchers(API + "/employees/*/compensation")
                    .hasRole("ADMIN")
                    .requestMatchers(GET, API + "/employees/export")
                    .hasRole("ADMIN")
                    .requestMatchers(API + "/sensitive-access-log", API + "/reports/**", API + "/users/**")
                    .hasRole("ADMIN")
                    .requestMatchers(POST, API + "/departments", API + "/locations")
                    .hasRole("ADMIN")
                    .requestMatchers(PUT, API + "/departments/*", API + "/locations/*")
                    .hasRole("ADMIN")
                    .requestMatchers(DELETE, API + "/departments/*", API + "/locations/*")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
