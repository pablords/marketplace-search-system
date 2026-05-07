package com.marketplace.search.search.interfaces.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Dados de entrada inválidos")
                .path(request.getDescription(false))
                .details(errors)
                .build();

        logger.warn("Erro de validação: {}", errors);

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Validation Failed");
        Span.current().setAttribute("error", true);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Violação de restrições")
                .path(request.getDescription(false))
                .build();

        logger.warn("Erro de constraint: {}", ex.getMessage());

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Constraint Violation");
        Span.current().setAttribute("error", true);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        logger.warn("Argumento inválido: {}", ex.getMessage());

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Invalid Argument: " + ex.getMessage());
        Span.current().setAttribute("error", true);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("Erro interno do servidor")
                .path(request.getDescription(false))
                .build();

        logger.error("Erro runtime: ", ex);

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Runtime Exception: " + ex.getMessage());
        Span.current().recordException(ex);
        Span.current().setAttribute("error", true);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("Erro inesperado do servidor")
                .path(request.getDescription(false))
                .build();

        logger.error("Erro não tratado: ", ex);

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Unhandled Exception: " + ex.getMessage());
        Span.current().setAttribute("error", true);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
