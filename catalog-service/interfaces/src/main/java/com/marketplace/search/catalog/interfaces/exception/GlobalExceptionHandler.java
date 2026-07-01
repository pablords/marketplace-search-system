package com.marketplace.search.catalog.interfaces.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.marketplace.search.catalog.domain.exceptions.ProductAlreadyExistsException;
import com.marketplace.search.catalog.domain.exceptions.TooManyRequestsException;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.validation.ConstraintViolation;
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
        
        Map<String, String> errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Violação de restrições")
                .path(request.getDescription(false))
                .details(errors)
                .build();

        logger.warn("Erro de constraint: {}", errors);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
                String message = ex.getMessage();
                ErrorResponse errorResponse = ErrorResponse.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Invalid Argument")
                                .message(message)
                                .path(request.getDescription(false))
                                .build();

                logger.warn("Argumento inválido: {}", message);

                return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointerException(
            NullPointerException ex, WebRequest request) {
        
        String message = ex.getMessage() != null ? ex.getMessage() : "Valor obrigatório não fornecido";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Null Pointer Exception")
                .message(message)
                .path(request.getDescription(false))
                .build();

        logger.error("NullPointerException: {}", message, ex);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProductAlreadyExistsException(
            ProductAlreadyExistsException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Product Already Exists")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        logger.warn("Tentativa de criar produto duplicado: {}", ex.getProductId());

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Product already exists: " + ex.getProductId());
        Span.current().setAttribute("error", true);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);

    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsException(
            TooManyRequestsException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        logger.warn("Concorrência excedida: {}", ex.getMessage());

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, "Too Many Requests");
        Span.current().setAttribute("error", true);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex, WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message("Recurso não encontrado: " + ex.getResourcePath())
                .path(request.getDescription(false))
                .build();

        // Log apenas em DEBUG para evitar poluição de logs com requisições esperadas (ex: Prometheus scraping)
        logger.debug("Recurso não encontrado: {}", ex.getResourcePath());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, WebRequest request) {
        
        String errorMessage = ex.getMostSpecificCause().getMessage();
        boolean isConflict = errorMessage != null && (errorMessage.contains("duplicate key") || errorMessage.contains("violates unique constraint"));

        HttpStatus status = isConflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        String errorType = isConflict ? "Conflict" : "Data Integrity Violation";
        String userMessage = isConflict ? "O registro já existe" : "Erro de integridade de dados (verifique chaves estrangeiras)";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(errorType)
                .message(userMessage)
                .path(request.getDescription(false))
                .build();

        logger.warn("{}: {}", errorType, errorMessage);

        // Mark current span as error in OpenTelemetry
        Span.current().setStatus(StatusCode.ERROR, errorType + ": " + errorMessage);
        Span.current().setAttribute("error", true);

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystemException(
            TransactionSystemException ex, WebRequest request) {
        
        // Percorre as causas para encontrar erro de duplicidade do Postgres/Hibernate
        Throwable cause = ex.getRootCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().contains("duplicate key")) {
             return handleProductAlreadyExistsException(new ProductAlreadyExistsException("via transaction"), request);
        }

        return handleRuntimeException(new RuntimeException("Erro de transação"), request);
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

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}