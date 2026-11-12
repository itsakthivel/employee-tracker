package com.example.tracker.employee.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.openapitools.jackson.nullable.JsonNullable;

/** The only fields an employee may change on their own record. Anything else is a 400. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class SelfUpdateRequest {
  public Long version;
  public JsonNullable<String> phone = JsonNullable.undefined();
  public JsonNullable<EmergencyContact> emergencyContact = JsonNullable.undefined();
}
