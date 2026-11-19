package co.nitida.shared;

import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Tiny JSON helper for audit details and stored snapshots. */
public final class Json {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static String of(Map<String, ?> fields) {
        return MAPPER.writeValueAsString(fields);
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }
}
