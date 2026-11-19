package co.nitida.shared;

/** Small text helpers shared by validation code. */
public final class Texts {
    private Texts() {}

    public static String requireLength(String value, int min, int max, String code, String message) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) throw ApiException.unprocessable(code, message);
        return v;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
