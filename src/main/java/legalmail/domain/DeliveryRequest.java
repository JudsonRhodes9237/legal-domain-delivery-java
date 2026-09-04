package legalmail.domain;

public record DeliveryRequest(Kind kind, String matterId, String recipient, String detail) {
    public enum Kind { MATTER_INTAKE, SIGNED_DOCUMENT, DEADLINE_FOLLOW_UP }
}
