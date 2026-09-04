package legalmail.service;

import legalmail.config.DeliveryConfig;
import legalmail.domain.DeliveryRequest;
import legalmail.domain.DeliveryResult;
import legalmail.infrai.InfraiEmailClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegalDeliveryDecisionTest {
    public static void main(String[] args) throws Exception {
        AtomicBoolean verified = new AtomicBoolean(false);
        AtomicBoolean sent = new AtomicBoolean(false);
        HttpServer fixture = HttpServer.create(new InetSocketAddress(0), 0);
        fixture.createContext("/v1/email/domain/get/mail.example.legal", exchange -> domain(exchange, verified.get()));
        fixture.createContext("/v1/email/send", exchange -> send(exchange, sent));
        fixture.start();
        try {
            DeliveryConfig config = new DeliveryConfig("test-key",
                    "http://localhost:" + fixture.getAddress().getPort(), "mail.example.legal", 0);
            LegalDeliveryService service = new LegalDeliveryService(config, new InfraiEmailClient(config));
            DeliveryRequest request = new DeliveryRequest(DeliveryRequest.Kind.SIGNED_DOCUMENT,
                    "MAT-1042", "client@example.com", "Engagement letter signed.");

            DeliveryResult pending = service.deliver(request);
            check("DOMAIN_VERIFICATION_PENDING".equals(pending.state()), "pending domain must block delivery");
            check(!sent.get(), "blocked delivery must not call email send");

            verified.set(true);
            DeliveryResult delivered = service.deliver(request);
            check("SENT".equals(delivered.state()), "verified domain must permit delivery");
            check("msg_fixture_1042".equals(delivered.messageId()), "message id must cross the boundary");
            check(sent.get(), "permitted delivery must call email send");
            System.out.println("PASS pending domain blocks; verified domain sends msg_fixture_1042");
        } finally {
            fixture.stop(0);
        }
    }

    private static void domain(HttpExchange exchange, boolean verified) throws IOException {
        check("GET".equals(exchange.getRequestMethod()), "domain lookup must be GET");
        respond(exchange, "{\"ok\":true,\"data\":{\"verification\":{\"status\":\""
                + (verified ? "verified" : "pending") + "\"}},\"metadata\":{}}");
    }

    private static void send(HttpExchange exchange, AtomicBoolean sent) throws IOException {
        check("POST".equals(exchange.getRequestMethod()), "email send must be POST");
        check(exchange.getRequestHeaders().getFirst("Idempotency-Key") != null, "write must be idempotent");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        check(body.contains("\"to\":\"client@example.com\""), "recipient must reach request boundary");
        sent.set(true);
        respond(exchange, "{\"ok\":true,\"data\":{\"message_id\":\"msg_fixture_1042\"},\"metadata\":{}}");
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
