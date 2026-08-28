package ru.tyomakr.akcp.ingestion.web.service;

import org.springframework.http.HttpStatus;

public class WebIngestionException extends RuntimeException {
  private final HttpStatus status;

  public WebIngestionException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public WebIngestionException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
