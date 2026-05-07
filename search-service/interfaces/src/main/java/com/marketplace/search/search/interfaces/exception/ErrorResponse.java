package com.marketplace.search.search.interfaces.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> details
) {
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> details;

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder status(int status) {
      this.status = status;
      return this;
    }

    public Builder error(String error) {
      this.error = error;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder path(String path) {
      this.path = path;
      return this;
    }

    public Builder details(Map<String, String> details) {
      this.details = details;
      return this;
    }

    public ErrorResponse build() {
      return new ErrorResponse(timestamp, status, error, message, path, details);
    }
  }
}
