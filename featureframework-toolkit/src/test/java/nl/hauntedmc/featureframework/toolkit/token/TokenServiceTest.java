package nl.hauntedmc.featureframework.toolkit.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenServiceTest {

    @Test
    void finiteTokenExpiresAfterItsConfiguredUses() {
        TokenService<String> tokens = new TokenService<>("test");
        String token = tokens.create(
                () -> CompletableFuture.completedFuture("payload"),
                TokenOptions.of(2, Duration.ofMinutes(1), true)
        );

        assertEquals(TokenResult.State.OK, tokens.consume(token).state());
        assertEquals(TokenResult.State.OK, tokens.consume(token).state());
        assertEquals(TokenResult.State.INVALID, tokens.consume(token).state());
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
        assertEquals(0, tokens.size());
    }
}
