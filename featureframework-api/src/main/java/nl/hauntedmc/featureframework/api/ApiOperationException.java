package nl.hauntedmc.featureframework.api;

import java.util.Objects;

/** Typed exception used to complete public asynchronous operations exceptionally. */
public final class ApiOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ApiFailureCode code;

    public ApiOperationException(ApiFailureCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ApiOperationException(ApiFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ApiFailureCode code() {
        return code;
    }
}
