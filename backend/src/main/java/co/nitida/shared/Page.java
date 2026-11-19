package co.nitida.shared;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Keyset pagination (spec §9): the listing stays a JSON array and the position of the next page travels in the
 * {@code X-Next-Cursor} header. The cursor is opaque to the client and is always validated, never trusted.
 */
public record Page<T>(List<T> items, String nextCursor) {
    public static final String HEADER = "X-Next-Cursor";
    public static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    /** Where the previous page ended: the last row's (createdAt, id). */
    public record Position(Instant at, UUID id) {}

    public static int limit(String raw) {
        if (raw == null) return DEFAULT_LIMIT;
        try {
            int n = Integer.parseInt(raw);
            if (n >= 1 && n <= MAX_LIMIT) return n;
        } catch (NumberFormatException ignored) { /* falls through to the same error */ }
        throw ApiException.unprocessable("INVALID_LIMIT", "El límite debe ser un número entre 1 y " + MAX_LIMIT);
    }

    /** Null cursor = first page. */
    public static Position position(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Position(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("INVALID_CURSOR", "El cursor no es válido: vuelva a pedir la primera página");
        }
    }

    /** {@code rows} were fetched with {@code limit + 1}: the extra row only says there is a next page. */
    public static <E, T> Page<T> of(List<E> rows, int limit, Function<E, Instant> at, Function<E, UUID> id, Function<E, T> view) {
        boolean more = rows.size() > limit;
        var page = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            var last = page.get(page.size() - 1);
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((at.apply(last) + "|" + id.apply(last)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new Page<>(page.stream().map(view).toList(), next);
    }
}
