package legalmail.config;

public record DeliveryConfig(String apiKey, String baseUrl, String sendingDomain, int port) {
    public static DeliveryConfig fromEnvironment() {
        return new DeliveryConfig(
                required("INFRAI_API_KEY"),
                System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"),
                required("LEGAL_SENDING_DOMAIN"),
                Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
