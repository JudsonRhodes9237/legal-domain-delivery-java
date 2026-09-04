package legalmail.infrai;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public InfraiException(String code, Map<String, Object> error, int httpStatus) {
        super(String.valueOf(error));
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public int clientStatus() { return httpStatus >= 400 && httpStatus < 500 ? httpStatus : 502; }
}
