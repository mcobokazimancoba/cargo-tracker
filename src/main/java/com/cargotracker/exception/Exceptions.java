package com.cargotracker.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// ─────────────────────────────────────────────
// Domain exceptions (package-private)
// ─────────────────────────────────────────────

class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(resourceName + " not found with identifier: " + identifier, 404);
    }
}

class BusinessRuleException extends AppException {
    public BusinessRuleException(String message) { super(message, 400); }
}

class InvalidStateTransitionException extends AppException {
    public InvalidStateTransitionException(String currentState, String attemptedOperation) {
        super("Cannot perform '" + attemptedOperation + "' on cargo in state: " + currentState, 409);
    }
}

class ForbiddenException extends AppException {
    public ForbiddenException(String message) { super(message, 403); }
}

class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) { super(message, 401); }
}

class DuplicateResourceException extends AppException {
    public DuplicateResourceException(String resourceName, String field, Object value) {
        super(resourceName + " already exists with " + field + ": " + value, 409);
    }
}

// ─────────────────────────────────────────────
// Public factory
// ─────────────────────────────────────────────

public final class Exceptions {

    private Exceptions() {}

    public static AppException badRequest(String message) {
        return new BusinessRuleException(message);  // ← FIXED
    }
    public static AppException notFound(String resourceName, Object identifier) {
        return new ResourceNotFoundException(resourceName, identifier);
    }
    public static AppException businessRule(String message) {
        return new BusinessRuleException(message);
    }
    public static AppException invalidState(String currentState, String attemptedOperation) {
        return new InvalidStateTransitionException(currentState, attemptedOperation);
    }
    public static AppException forbidden(String message) {
        return new ForbiddenException(message);
    }
    public static AppException unauthorized(String message) {
        return new UnauthorizedException(message);
    }
    public static AppException duplicate(String resourceName, String field, Object value) {
        return new DuplicateResourceException(resourceName, field, value);
    }
}

// ─────────────────────────────────────────────
// Mappers
// ─────────────────────────────────────────────

@Provider
class AppExceptionMapper implements ExceptionMapper<AppException> {
    @Override
    public Response toResponse(AppException ex) {
        return ErrorResponses.errorResponse(
                ex.getHttpStatusCode(),
                ErrorResponses.httpText(ex.getHttpStatusCode()),
                ex.getMessage()
        );
    }
}

@Provider
class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(cv -> fieldName(cv) + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponses.errorResponse(400, "Validation Error", message);
    }

    private String fieldName(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}

@Provider
class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException ex) {
        int status = ex.getResponse().getStatus();
        String text = ErrorResponses.httpText(status);
        String msg  = ex.getMessage() != null ? ex.getMessage() : text;
        return ErrorResponses.errorResponse(status, text, msg);
    }
}

@Provider
class CatchAllExceptionMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable ex) {
        if (ex instanceof AppException appEx) {
            return new AppExceptionMapper().toResponse(appEx);
        }
        if (ex instanceof WebApplicationException webEx) {
            return new WebApplicationExceptionMapper().toResponse(webEx);
        }
        ex.printStackTrace();
        return ErrorResponses.errorResponse(500, "Internal Server Error",
                "An unexpected error occurred. Check the server log for details.");
    }
}

// ─────────────────────────────────────────────
// Shared helper
// ─────────────────────────────────────────────

final class ErrorResponses {
    private ErrorResponses() {}

    static Response errorResponse(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        return Response.status(status)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(body)
                       .build();
    }

    static String httpText(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            default  -> "Internal Server Error";
        };
    }
}