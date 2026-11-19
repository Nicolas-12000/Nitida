package co.nitida.motorcycles.domain;

import co.nitida.shared.ApiException;
import java.util.regex.Pattern;

/** RN-10..12: normalization (idempotent) and validation of plates and VINs. */
public final class VehicleIdentifiers {
    private static final Pattern PLATE = Pattern.compile("^[A-Z]{3}[0-9]{2}[A-Z]$");
    private static final Pattern LEGACY_PLATE = Pattern.compile("^[A-Z]{3}[0-9]{2}$");
    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$"); // no I, O, Q

    public record Plate(String value, boolean legacy) {}

    private VehicleIdentifiers() {}

    /** Uppercase, no spaces, dashes or dots. Returns null for null/blank. Applying it twice changes nothing. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String n = raw.replaceAll("[\\s\\-.]", "").toUpperCase();
        return n.isEmpty() ? null : n;
    }

    public static Plate plate(String raw) {
        String p = normalize(raw);
        if (p != null && PLATE.matcher(p).matches()) return new Plate(p, false);
        if (p != null && LEGACY_PLATE.matcher(p).matches()) return new Plate(p, true);
        throw ApiException.unprocessable("INVALID_PLATE", "La placa no tiene un formato válido");
    }

    public static String vin(String raw) {
        String v = normalize(raw);
        if (v == null || !VIN.matcher(v).matches()) {
            throw ApiException.unprocessable("INVALID_VIN", "El VIN debe tener 17 caracteres y no usar I, O ni Q");
        }
        return v;
    }

    /** RN-73: the public page shows ABC**D, never the whole plate. */
    public static String maskPlate(String plate) {
        if (plate == null) return null;
        return plate.length() == 6 ? plate.substring(0, 3) + "**" + plate.charAt(5) : plate.substring(0, 3) + "**";
    }
}
