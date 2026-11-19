package co.nitida.shared;

/** Colombian phone numbers stored in E.164 (+57 followed by 10 digits). */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    public static String normalize(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
        if (digits.matches("^3[0-9]{9}$") || digits.matches("^60[0-9]{8}$")) return "+57" + digits;
        if (digits.matches("^\\+57[0-9]{10}$")) return digits;
        if (digits.matches("^57[0-9]{10}$")) return "+" + digits;
        throw ApiException.unprocessable("INVALID_PHONE", "El teléfono no es válido");
    }
}
