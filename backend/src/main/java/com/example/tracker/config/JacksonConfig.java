package com.example.tracker.config;

import com.fasterxml.jackson.databind.Module;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
  /** Lets PATCH bodies tell "field absent" apart from "field set to null". */
  @Bean
  Module jsonNullableModule() {
    return new JsonNullableModule();
  }
}
