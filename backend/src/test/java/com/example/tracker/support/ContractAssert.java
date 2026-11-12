package com.example.tracker.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import java.nio.file.Path;
import org.springframework.test.web.servlet.ResultMatcher;

/** Checks responses against specs/001-employee-tracker/contracts/openapi.yaml. */
public final class ContractAssert {

  private static final OpenApiInteractionValidator VALIDATOR =
      OpenApiInteractionValidator.createForSpecificationUrl(
              Path.of("../specs/001-employee-tracker/contracts/openapi.yaml")
                  .toAbsolutePath()
                  .normalize()
                  .toUri()
                  .toString())
          .withLevelResolver(
              LevelResolver.create()
                  .withLevel("validation.request", ValidationReport.Level.IGNORE)
                  // The validator tries to parse plain-string headers such as Location as JSON.
                  .withLevel("validation.response.header", ValidationReport.Level.IGNORE)
                  .build())
          .build();

  private ContractAssert() {}

  public static ResultMatcher matchesContract() {
    return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
  }
}
