package co.nitida.catalog.domain;

import co.nitida.shared.ApiException;
import co.nitida.shared.crypto.Sha256;

/** RN-94: uppercase, no spaces, 4..64 characters of [A-Z0-9-/.]. Normalizing twice changes nothing. */
public record SerialNumber(String value) {
    public static SerialNumber of(String raw) {
        String v = raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase();
        if (!v.matches("^[A-Z0-9\\-/.]{4,64}$")) {
            throw ApiException.unprocessable("INVALID_SERIAL", "El serial debe tener de 4 a 64 caracteres (letras, números, - / .)");
        }
        return new SerialNumber(v);
    }

    /** RN-97: the public content only ever carries this hash. */
    public String hash() {
        return Sha256.hexOf(value);
    }
}
