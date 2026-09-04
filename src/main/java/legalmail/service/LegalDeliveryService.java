package legalmail.service;

import legalmail.config.DeliveryConfig;
import legalmail.domain.DeliveryRequest;
import legalmail.domain.DeliveryResult;
import legalmail.infrai.InfraiEmailClient;
import java.util.Map;

public final class LegalDeliveryService {
    private final DeliveryConfig config;
    private final InfraiEmailClient infrai;

    public LegalDeliveryService(DeliveryConfig config, InfraiEmailClient infrai) {
        this.config = config;
        this.infrai = infrai;
    }

    public Map<String, Object> onboardDomain() {
        return infrai.verifyDomain(config.sendingDomain(), "domain-onboard:" + config.sendingDomain());
    }

    public DeliveryResult deliver(DeliveryRequest request) {
        Map<String, Object> domain = infrai.getDomain(config.sendingDomain());
        Map<String, Object> verification = nested(domain, "verification");
        if (!"verified".equalsIgnoreCase(String.valueOf(verification.get("status")))) {
            return new DeliveryResult(request.matterId(), null, "DOMAIN_VERIFICATION_PENDING");
        }
        String subject = switch (request.kind()) {
            case MATTER_INTAKE -> "Matter " + request.matterId() + " intake received";
            case SIGNED_DOCUMENT -> "Signed document for matter " + request.matterId();
            case DEADLINE_FOLLOW_UP -> "Deadline follow-up for matter " + request.matterId();
        };
        String html = "<p>Matter <strong>" + escape(request.matterId()) + "</strong></p><p>"
                + escape(request.detail()) + "</p>";
        String messageId = infrai.send(request.recipient(), subject, html,
                "matters@" + config.sendingDomain(), "matter:" + request.matterId() + ":" + request.kind());
        return new DeliveryResult(request.matterId(), messageId, "SENT");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
