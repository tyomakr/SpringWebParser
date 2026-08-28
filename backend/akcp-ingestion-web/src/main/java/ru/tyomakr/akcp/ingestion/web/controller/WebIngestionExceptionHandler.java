package ru.tyomakr.akcp.ingestion.web.controller;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.tyomakr.akcp.ingestion.web.service.WebIngestionException;
import ru.tyomakr.akcp.library.dto.ErrorResponse;

@RestControllerAdvice(assignableTypes = WebIngestionController.class)
public class WebIngestionExceptionHandler {
  @ExceptionHandler(WebIngestionException.class)
  public ResponseEntity<ErrorResponse> handleWebIngestionException(WebIngestionException ex) {
    return ResponseEntity.status(ex.status())
        .body(new ErrorResponse(ex.getMessage(), Instant.now()));
  }
}
