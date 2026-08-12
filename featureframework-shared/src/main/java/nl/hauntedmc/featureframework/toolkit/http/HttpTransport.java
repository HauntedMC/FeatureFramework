package nl.hauntedmc.featureframework.toolkit.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Compatibility helper for legacy callers; new code must inject {@link AsyncHttpTransport}. */
public final class HttpTransport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpTransport() {
    }

    public static String post(String url, List<FormParameter> args) throws IOException, InterruptedException {
        return post(url, args, CLIENT, false);
    }

    /**
     * Sends a form request while requiring HTTPS for both the request URI and the
     * final response URI after redirects.
     */
    public static String postHttps(String url, List<FormParameter> args) throws IOException, InterruptedException {
        return post(url, args, CLIENT, true);
    }

    /** Sends a bounded JSON request over HTTPS using the shared transport and timeout policy. */
    public static String postJsonHttps(String url, String payload) throws IOException, InterruptedException {
        return send(url, "application/json", payload == null ? "" : payload, CLIENT, true);
    }

    static String post(String url, List<FormParameter> args, HttpClient httpClient)
            throws IOException, InterruptedException {
        return post(url, args, httpClient, false);
    }

    static String postHttps(String url, List<FormParameter> args, HttpClient httpClient)
            throws IOException, InterruptedException {
        return post(url, args, httpClient, true);
    }

    private static String post(
            String url,
            List<FormParameter> args,
            HttpClient httpClient,
            boolean requireHttps
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(httpClient, "httpClient");
        List<FormParameter> safeArgs = List.copyOf(Objects.requireNonNull(args, "args"));
        String form = safeArgs.stream()
                .map(p -> encodeFormComponent(p.name()) + "=" + encodeFormComponent(p.value()))
                .collect(Collectors.joining("&"));

        return send(url, "application/x-www-form-urlencoded", form, httpClient, requireHttps);
    }

    private static String send(
            String url,
            String contentType,
            String body,
            HttpClient httpClient,
            boolean requireHttps
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(httpClient, "httpClient");
        URI uri = URI.create(Objects.requireNonNull(url, "url"));
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IOException("Unsupported URI scheme for HTTP client: " + scheme);
        }
        if (requireHttps && !scheme.equalsIgnoreCase("https")) {
            throw new IOException("HTTPS is required for this request");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (requireHttps && !"https".equalsIgnoreCase(response.uri().getScheme())) {
            InputStream responseBody = response.body();
            if (responseBody != null) {
                responseBody.close();
            }
            throw new IOException("HTTPS is required for the final response");
        }
        String responseText;
        try (InputStream stream = response.body()) {
            byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new IOException("Response too large");
            }
            responseText = new String(bytes, StandardCharsets.UTF_8);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return responseText;
        } else {
            throw new IOException("Unexpected response code: " + response.statusCode());
        }
    }

    private static String encodeFormComponent(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * One application/x-www-form-urlencoded request parameter.
     *
     * @param name parameter name
     * @param value parameter value, or {@code null} for an empty value
     */
    public record FormParameter(String name, String value) {
        public FormParameter {
            Objects.requireNonNull(name, "name");
        }
    }
}
