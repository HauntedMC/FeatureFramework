package nl.hauntedmc.featureframework.toolkit.token;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of consuming a token.
 */
public final class TokenResult<T> {

    public enum State {
        OK,        // payload present
        EXPIRED,   // expired or uses exhausted
        LOADING,   // payload still loading
        EMPTY,     // payload loaded but empty (null or filtered out)
        INVALID    // token not found / unknown
    }

    private final State state;
    private final T payload;

    private TokenResult(State state, T payload) {
        this.state = Objects.requireNonNull(state, "state");
        this.payload = payload;
    }

    public static <T> TokenResult<T> ok(T payload) {
        return new TokenResult<>(State.OK, payload);
    }

    public static <T> TokenResult<T> expired() {
        return new TokenResult<>(State.EXPIRED, null);
    }

    public static <T> TokenResult<T> loading() {
        return new TokenResult<>(State.LOADING, null);
    }

    public static <T> TokenResult<T> empty() {
        return new TokenResult<>(State.EMPTY, null);
    }

    public static <T> TokenResult<T> invalid() {
        return new TokenResult<>(State.INVALID, null);
    }

    public State state() {
        return state;
    }

    public boolean isOk() {
        return state == State.OK;
    }

    public T payload() {
        return payload;
    }

    /** Returns the payload as an Optional for callers that prefer null-free result handling. */
    public Optional<T> payloadOptional() {
        return Optional.ofNullable(payload);
    }
}
