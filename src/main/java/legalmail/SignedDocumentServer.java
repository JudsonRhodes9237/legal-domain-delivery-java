package legalmail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import legalmail.config.DeliveryConfig;
import legalmail.domain.DeliveryRequest;
import legalmail.domain.DeliveryResult;
import legalmail.infrai.InfraiEmailClient;
import legalmail.infrai.InfraiException;
import legalmail.service.LegalDeliveryService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class SignedDocumentServer {
    public static void main(String[] args) throws IOException {
        DeliveryConfig config = DeliveryConfig.fromEnvironment();
        LegalDeliveryService service = new LegalDeliveryService(config, new InfraiEmailClient(config));
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/domains/onboard", exchange -> handleOnboard(exchange, service));
        server.createContext("/deliveries", exchange -> handleDelivery(exchange, service));
        server.start();
        System.out.println("Legal delivery service listening on " + config.port());
    }

    private static void handleOnboard(HttpExchange exchange, LegalDeliveryService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { reply(exchange, 405, "{\"error\":\"method\"}"); return; }
        try { service.onboardDomain(); reply(exchange, 202, "{\"state\":\"DNS_VERIFICATION_REQUESTED\"}"); }
        catch (InfraiException e) { reply(exchange, e.clientStatus(), error(e)); }
    }

    private static void handleDelivery(HttpExchange exchange, LegalDeliveryService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) { reply(exchange, 405, "{\"error\":\"method\"}"); return; }
        try {
            String[] fields = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).strip().split("\\|", -1);
            if (fields.length != 4) { reply(exchange, 400, "{\"error\":\"kind|matterId|recipient|detail required\"}"); return; }
            DeliveryRequest request = new DeliveryRequest(DeliveryRequest.Kind.valueOf(fields[0]), fields[1], fields[2], fields[3]);
            DeliveryResult result = service.deliver(request);
            reply(exchange, 200, "{\"matter_id\":\"" + result.matterId() + "\",\"state\":\"" + result.state()
                    + "\",\"message_id\":\"" + String.valueOf(result.messageId()) + "\"}");
        } catch (IllegalArgumentException e) { reply(exchange, 400, "{\"error\":\"invalid delivery kind\"}"); }
        catch (InfraiException e) { reply(exchange, e.clientStatus(), error(e)); }
    }

    private static String error(InfraiException e) { return "{\"error\":\"" + e.code() + "\"}"; }
    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
