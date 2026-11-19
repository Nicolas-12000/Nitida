package co.nitida.shared;

/** Small text helpers shared by validation code. */
public final class Texts {
    private Texts() {}

    public static String requireLength(String value, int min, int max, String code, String message) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) throw ApiException.unprocessable(code, message);
        return v;
    }

    // PII sniffing, not RFC 5322: one pass, no nested quantifiers (Sonar S8786).
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    /** 8 to 30 digits with a single space, dot or dash between them: phones (310 123 4567) and national ids (1.085.123.456). */
    private static final java.util.regex.Pattern LONG_NUMBER = java.util.regex.Pattern.compile("\\d(?:[\\s.\\-]?\\d){7,29}");

    /**
     * For free text that ends up in the public, signed, immutable history (RN-22): same length rules, and no emails,
     * phones or national ids. Prices and mileage stay under 8 digits, so ordinary workshop text passes.
     */
    public static String requirePublic(String value, int min, int max, String code, String message) {
        String v = requireLength(value, min, max, code, message);
        if (EMAIL.matcher(v).find() || LONG_NUMBER.matcher(v).find()) {
            throw ApiException.unprocessable("PERSONAL_DATA",
                    "Este texto es público y no puede llevar correos, teléfonos ni cédulas: quítelos y use el cliente de la orden");
        }
        return v;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
