package co.nitida.catalog.domain;

import co.nitida.shared.ApiException;

/** RN-92: EAN-13, EAN-8, UPC-A and GTIN-14: digits only, valid mod-10 check digit (weights 3/1). */
public record ProductCode(String value, String type) {
    public static ProductCode of(String raw) {
        String v = raw == null ? "" : raw.strip();
        if (!v.matches("\\d+") || !(v.length() == 8 || v.length() == 12 || v.length() == 13 || v.length() == 14)) {
            throw invalid();
        }
        int expected = checkDigit(v.substring(0, v.length() - 1));
        if (expected != v.charAt(v.length() - 1) - '0') throw invalid();
        String type = switch (v.length()) {
            case 8 -> "EAN_8";
            case 12 -> "UPC_A";
            case 13 -> "EAN_13";
            default -> "GTIN_14";
        };
        return new ProductCode(v, type);
    }

    /** Weights 3,1,3,1... starting from the digit next to the check digit. */
    public static int checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(body.length() - 1 - i) - '0';
            sum += digit * (i % 2 == 0 ? 3 : 1);
        }
        return (10 - sum % 10) % 10;
    }

    private static ApiException invalid() {
        return ApiException.unprocessable("INVALID_PRODUCT_CODE", "El código de producto no es válido, vuelva a escanearlo");
    }
}
