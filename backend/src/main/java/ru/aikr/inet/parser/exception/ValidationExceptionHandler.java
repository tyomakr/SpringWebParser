package ru.aikr.inet.parser.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик ошибок валидации для WebFlux
 */
@Slf4j
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(
            WebExchangeBindException ex) {
        
        log.warn("Validation error: {}", ex.getMessage());
        
        Map<String, Object> errors = new HashMap<>();
        errors.put("status", HttpStatus.BAD_REQUEST.value());
        errors.put("error", "Validation failed");
        
        // Собираем все ошибки валидации
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null 
                                ? error.getDefaultMessage() 
                                : "Invalid value",
                        (existing, replacement) -> existing + "; " + replacement
                ));
        
        errors.put("fieldErrors", fieldErrors);
        
        // Общие ошибки
        if (!ex.getGlobalErrors().isEmpty()) {
            errors.put("globalErrors", ex.getGlobalErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.toList()));
        }
        
        return Mono.just(ResponseEntity.badRequest().body(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        
        log.warn("Constraint violation: {}", ex.getMessage());
        
        Map<String, Object> errors = new HashMap<>();
        errors.put("status", HttpStatus.BAD_REQUEST.value());
        errors.put("error", "Validation failed");
        
        Map<String, String> fieldErrors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> {
                            String path = violation.getPropertyPath().toString();
                            // Извлекаем имя параметра из пути (например, "getImagesFromPages.num1" -> "num1")
                            int lastDot = path.lastIndexOf('.');
                            return lastDot >= 0 ? path.substring(lastDot + 1) : path;
                        },
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing + "; " + replacement
                ));
        
        errors.put("fieldErrors", fieldErrors);
        
        return Mono.just(ResponseEntity.badRequest().body(errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        Map<String, Object> errors = new HashMap<>();
        errors.put("status", HttpStatus.BAD_REQUEST.value());
        errors.put("error", ex.getMessage());
        
        return Mono.just(ResponseEntity.badRequest().body(errors));
    }
}

