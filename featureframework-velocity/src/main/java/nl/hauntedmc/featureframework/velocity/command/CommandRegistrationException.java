package nl.hauntedmc.featureframework.velocity.command;

/** Raised when a feature-owned command cannot be registered completely. */
public final class CommandRegistrationException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public CommandRegistrationException(String message) {
        super(message);
    }

    public CommandRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
