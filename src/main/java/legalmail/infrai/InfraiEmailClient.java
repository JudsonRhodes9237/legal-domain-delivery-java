package legalmail.infrai;

import legalmail.config.DeliveryConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiEmailClient {
    private final HttpClient http;
    private final DeliveryConfig config;

    public InfraiEmailClient(DeliveryConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    InfraiEmailClient(DeliveryConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> verifyDomain(String domain, String idempotencyKey) {
        return request("POST", "/v1/email/domain/verify", Map.of("domain", domain), idempotencyKey);
    }

    public Map<String, Object> getDomain(String domain) {
        return request("GET", "/v1/email/domain/get/" + domain, null, null);
    }

    public String send(String to, String subject, String html, String from, String idempotencyKey) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("to", to); body.put("subject", subject); body.put("html", html); body.put("from", from);
        return String.valueOf(request("POST", "/v1/email/send", body, idempotencyKey).get("message_id"));
    }

    private Map<String, Object> request(String method, String path, Map<String, String> body, String key) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                        .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + config.apiKey())
                        .header("Accept", "application/json");
                if (key != null) builder.header("Idempotency-Key", key);
                if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
                else builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(Json.encode(body)));
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Map<String, Object> envelope = Json.object(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryDelay(response, attempt));
                    continue;
                }
                if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                    Map<String, Object> error = nested(envelope, "error");
                    throw new InfraiException(String.valueOf(error.get("code")), error, response.statusCode());
                }
                return nested(envelope, "data");
            } catch (IOException e) {
                throw new IllegalStateException("Email transport failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Email request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(Long::parseLong).map(seconds -> seconds * 1000)
                .orElse(250L * (1L << attempt));
    }

    private static Map<String, Object> nested(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> nested)) return Map.of();
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) nested;
        return result;
    }
}
