package nl.hauntedmc.featureframework.toolkit.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    @Test
    void finiteTokenExpiresAfterItsConfiguredUses() {
        TokenService<String> tokens = new TokenService<>("test");
        String token = tokens.create(
                () -> CompletableFuture.completedFuture("payload"),
                TokenOptions.of(2, Duration.ofMinutes(1), true)
        );

        TokenResult<String> first = tokens.consume(token);
        assertEquals(TokenResult.State.OK, first.state());
        assertTrue(first.isOk());
        assertEquals(Optional.of("payload"), first.payloadOptional());
        assertEquals(TokenResult.State.OK, tokens.consume(token).state());
        TokenResult<String> invalid = tokens.consume(token);
        assertEquals(TokenResult.State.INVALID, invalid.state());
        assertFalse(invalid.isOk());
        assertTrue(invalid.payloadOptional().isEmpty());
        assertEquals(0, tokens.size());
    }

    @Test
    void loadingAndEmptyPayloadStatesAreExplicit() {
        TokenService<String> tokens = new TokenService<>("test");
        CompletableFuture<String> loader = new CompletableFuture<>();
        String loadingToken = tokens.create(() -> loader, TokenOptions.infinite());

        assertEquals(TokenResult.State.LOADING, tokens.consume(loadingToken).state());
        loader.complete(null);
        assertEquals(TokenResult.State.EMPTY, tokens.consume(loadingToken).state());
        assertEquals(1, tokens.size());

        tokens.revoke(loadingToken);
        assertTrue(tokens.isEmpty());
    }

    @Test
    void acceptsGenericCompletionStagesAndSupportsLifecycleConveniences() {
        TokenService<String> tokens = new TokenService<>("test-namespace");
        CompletionStage<String> stage = CompletableFuture.completedFuture("value");

        String first = tokens.create(() -> stage);
        String second = tokens.create(() -> CompletableFuture.completedFuture("other"));

        assertEquals("test-namespace", tokens.namespace());
        assertEquals("value", tokens.consume(first).payload());
        assertEquals(2, tokens.size());
        tokens.clear();
        assertTrue(tokens.isEmpty());
        assertEquals(TokenResult.State.INVALID, tokens.consume(second).state());
    }
}
