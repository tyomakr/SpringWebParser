package ru.tyomakr.akcp.library.controller;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.tyomakr.akcp.library.dto.ErrorResponse;
import ru.tyomakr.akcp.library.service.ItemNotFoundException;

@RestControllerAdvice
public class RestExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleBadRequest(IllegalArgumentException ex) {
    return new ErrorResponse(ex.getMessage(), Instant.now());
  }

  @ExceptionHandler(ItemNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleNotFound(ItemNotFoundException ex) {
    return new ErrorResponse(ex.getMessage(), Instant.now());
  }
}
