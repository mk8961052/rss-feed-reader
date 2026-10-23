package com.rssfeedreader.config;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Frontend origins allowed to call the {@code /api/**} endpoints. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties(@Nullable List<String> allowedOrigins) {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      throw new IllegalStateException("app.cors.allowed-origins must list at least one origin.");
    }
    this.allowedOrigins = List.copyOf(allowedOrigins);
  }
}
