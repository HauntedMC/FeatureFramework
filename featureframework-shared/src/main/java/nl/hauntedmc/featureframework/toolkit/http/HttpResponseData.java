package nl.hauntedmc.featureframework.toolkit.http;

import java.net.URI;
import java.util.Objects;

/** Bounded asynchronous HTTP response. */
public record HttpResponseData(int statusCode, URI uri, String body) {
    public HttpResponseData {
        Objects.requireNonNull(uri, "uri");
        body = body == null ? "" : body;
    }

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
