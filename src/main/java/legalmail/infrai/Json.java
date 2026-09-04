package legalmail.infrai;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private final String source;
    private int offset;

    private Json(String source) { this.source = source; }

    static Map<String, Object> object(String source) {
        Object value = new Json(source).readValue();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON object expected");
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    static String encode(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (out.length() > 1) out.append(',');
            out.append(quote(field.getKey())).append(':').append(quote(field.getValue()));
        }
        return out.append('}').toString();
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private Object readValue() {
        space();
        char c = source.charAt(offset);
        if (c == '{') return readObject();
        if (c == '"') return readString();
        if (source.startsWith("true", offset)) { offset += 4; return true; }
        if (source.startsWith("false", offset)) { offset += 5; return false; }
        if (source.startsWith("null", offset)) { offset += 4; return null; }
        int start = offset;
        while (offset < source.length() && ",}] \n\r\t".indexOf(source.charAt(offset)) < 0) offset++;
        return source.substring(start, offset);
    }

    private Map<String, Object> readObject() {
        offset++;
        Map<String, Object> map = new LinkedHashMap<>();
        space();
        if (source.charAt(offset) == '}') { offset++; return map; }
        while (true) {
            space(); String key = readString(); space(); offset++;
            map.put(key, readValue()); space();
            if (source.charAt(offset++) == '}') return map;
        }
    }

    private String readString() {
        offset++;
        StringBuilder out = new StringBuilder();
        while (offset < source.length()) {
            char c = source.charAt(offset++);
            if (c == '"') return out.toString();
            if (c == '\\') {
                char escaped = source.charAt(offset++);
                out.append(switch (escaped) {
                    case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'b' -> '\b'; case 'f' -> '\f'; default -> escaped;
                });
            } else out.append(c);
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private void space() {
        while (offset < source.length() && Character.isWhitespace(source.charAt(offset))) offset++;
    }
}
