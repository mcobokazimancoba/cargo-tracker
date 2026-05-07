package com.cargotracker.exception;

/**
 * Base class for all application-specific runtime exceptions.
 *
 * <p>Using a common base lets the JAX-RS ExceptionMapper catch all domain
 * exceptions in one place and map them to the correct HTTP response codes.
 *
 * <p>All exceptions in this application are unchecked (extend RuntimeException).
 * Checked exceptions are unsuitable for Jakarta EE service layers because:
 * <ul>
 *   <li>CDI interceptors (e.g. {@code @Transactional}) do not roll back transactions
 *       on checked exceptions by default.</li>
 *   <li>JAX-RS resources cannot propagate checked exceptions to ExceptionMappers.</li>
 * </ul>
 */
public abstract class AppException extends RuntimeException {

    /*
     * All Throwable subclasses are Serializable. Java requires serialVersionUID
     * on any Serializable class to make the version explicit. Without it the
     * compiler generates a warning and the JVM synthesises one from class metadata
     * — meaning any change to the class breaks deserialisation silently.
     * 1L is the conventional starting value; increment if the class changes shape.
     */
    private static final long serialVersionUID = 1L;

    private final int httpStatusCode;

    protected AppException(String message, int httpStatusCode) {
        super(message);
        this.httpStatusCode = httpStatusCode;
    }

    protected AppException(String message, int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }
}