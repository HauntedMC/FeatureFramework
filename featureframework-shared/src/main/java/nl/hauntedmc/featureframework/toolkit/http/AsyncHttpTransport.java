package nl.hauntedmc.featureframework.toolkit.http;

import java.net.URI;
import java.util.concurrent.CompletionStage;

/** Injectable non-blocking HTTP transport. */
@FunctionalInterface
public interface AsyncHttpTransport {
    CompletionStage<HttpResponseData> post(URI uri, String contentType, String body, boolean requireHttps);
}
