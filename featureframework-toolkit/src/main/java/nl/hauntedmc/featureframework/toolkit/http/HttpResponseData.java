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

    public boolean redirect() {
        return statusCode >= 300 && statusCode < 400;
    }

    public boolean clientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean serverError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean error() {
        return clientError() || serverError();
    }
}
