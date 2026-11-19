package co.nitida.shared;

/** Small text helpers shared by validation code. */
public final class Texts {
    private Texts() {}

    public static String requireLength(String value, int min, int max, String code, String message) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) throw ApiException.unprocessable(code, message);
        return v;
    }

    /**
     * For free text that ends up in the public, signed, immutable history (RN-22): same length rules, and no emails,
     * phones or national ids. Prices and mileage stay under 8 digits, so ordinary workshop text passes.
     */
    public static String requirePublic(String value, int min, int max, String code, String message) {
        String v = requireLength(value, min, max, code, message);
        if (containsEmailLike(v) || containsLongDigitRun(v)) {
            throw ApiException.unprocessable("PERSONAL_DATA",
                    "Este texto es público y no puede llevar correos, teléfonos ni cédulas: quítelos y use el cliente de la orden");
        }
        return v;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static boolean containsEmailLike(String v) {
        for (int at = 0; at < v.length(); at++) {
            if (v.charAt(at) != '@') continue;
            int localStart = at;
            while (localStart > 0 && emailLocalChar(v.charAt(localStart - 1))) localStart--;
            if (localStart == at) continue;
            int hostStart = at + 1;
            if (hostStart >= v.length()) continue;
            int hostEnd = hostStart;
            while (hostEnd < v.length() && emailHostChar(v.charAt(hostEnd))) hostEnd++;
            if (hostEnd == hostStart) continue;
            int lastDot = -1;
            for (int i = hostStart; i < hostEnd; i++) {
                if (v.charAt(i) == '.') lastDot = i;
            }
            if (lastDot < hostStart + 1 || lastDot >= hostEnd - 1) continue;
            int letters = 0;
            for (int i = lastDot + 1; i < hostEnd; i++) {
                if (Character.isLetter(v.charAt(i))) letters++;
            }
            if (letters >= 2) return true;
        }
        return false;
    }

    static boolean containsLongDigitRun(String v) {
        for (int start = 0; start < v.length(); start++) {
            if (!Character.isDigit(v.charAt(start))) continue;
            int digits = 1;
            int i = start + 1;
            while (i < v.length() && digits < 31) {
                char c = v.charAt(i);
                if (Character.isDigit(c)) {
                    digits++;
                    i++;
                } else if ((c == ' ' || c == '.' || c == '-') && i + 1 < v.length() && Character.isDigit(v.charAt(i + 1))) {
                    i++;
                } else {
                    break;
                }
            }
            if (digits >= 8) return true;
        }
        return false;
    }

    private static boolean emailLocalChar(int c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '%' || c == '+' || c == '-';
    }

    private static boolean emailHostChar(int c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '.';
    }
}