package nl.hauntedmc.featureframework.toolkit.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** JDK transport with explicit redirect policy and a bounded response body. */
public final class JdkAsyncHttpTransport implements AsyncHttpTransport {
    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final HttpClient client;
    private final Duration timeout;

    public JdkAsyncHttpTransport(HttpClient client, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public static JdkAsyncHttpTransport defaults() {
        return new JdkAsyncHttpTransport(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Duration.ofSeconds(8));
    }

    @Override
    public CompletionStage<HttpResponseData> post(URI uri, String contentType, String body, boolean requireHttps) {
        Objects.requireNonNull(uri, "uri");
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || (requireHttps && !scheme.equalsIgnoreCase("https"))) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported HTTP URI"));
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Response too large");
            }
            return new HttpResponseData(response.statusCode(), response.uri(),
                    new String(response.body(), StandardCharsets.UTF_8));
        });
    }
}
