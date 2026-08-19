package nl.hauntedmc.featureframework.toolkit.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class HttpResponseDataTest {
    @Test
    void statusHelpersClassifyCommonHttpResponses() {
        HttpResponseData ok = new HttpResponseData(204, URI.create("https://example.com"), null);
        HttpResponseData redirect = new HttpResponseData(302, URI.create("https://example.com"), "");
        HttpResponseData clientError = new HttpResponseData(404, URI.create("https://example.com"), "missing");
        HttpResponseData serverError = new HttpResponseData(503, URI.create("https://example.com"), "down");

        assertTrue(ok.successful());
        assertEquals("", ok.body());
        assertTrue(redirect.redirect());
        assertTrue(clientError.clientError());
        assertTrue(clientError.error());
        assertTrue(serverError.serverError());
        assertTrue(serverError.error());
        assertFalse(serverError.successful());
    }

    @Test
    void transportProvidesJsonConvenienceWithoutChangingFunctionalContract() {
        AsyncHttpTransport transport = (uri, contentType, body, requireHttps) -> {
            assertEquals("application/json", contentType);
            assertTrue(requireHttps);
            return CompletableFuture.completedFuture(new HttpResponseData(200, uri, body));
        };

        HttpResponseData response = transport.postJson(URI.create("https://example.com"), "{}", true)
                .toCompletableFuture().join();
        assertEquals("{}", response.body());
    }
}
