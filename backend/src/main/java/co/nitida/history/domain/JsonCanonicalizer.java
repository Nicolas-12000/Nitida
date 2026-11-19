package co.nitida.history.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON Canonicalization Scheme (RFC 8785): keys sorted by UTF-16 code units, no whitespace, minimal string
 * escapes. shortcut: integers only (no fractional numbers), because the history never stores them;
 * add ECMAScript number serialization before adding a decimal field.
 */
public final class JsonCanonicalizer {
    private JsonCanonicalizer() {}

    public static String canonicalize(Object value) {
        var sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case Boolean b -> sb.append(b ? "true" : "false");
            case Number n -> {
                if (n instanceof Double || n instanceof Float || n instanceof java.math.BigDecimal) {
                    throw new IllegalArgumentException("Only integers are supported");
                }
                sb.append(n);
            }
            case CharSequence s -> string(sb, s.toString());
            case Map<?, ?> m -> {
                var sorted = new TreeMap<String, Object>(); // String.compareTo orders by UTF-16 code units, as the RFC requires
                m.forEach((k, val) -> sorted.put((String) k, val));
                sb.append('{');
                boolean first = true;
                for (var e : sorted.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    string(sb, e.getKey());
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
            }
            case List<?> l -> {
                sb.append('[');
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(',');
                    write(sb, l.get(i));
                }
                sb.append(']');
            }
            default -> throw new IllegalArgumentException("Unsupported JSON value: " + v.getClass().getName());
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    boolean loneSurrogate = Character.isSurrogate(c)
                            && !(Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || loneSurrogate) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
