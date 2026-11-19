package co.nitida.identity.domain;

import co.nitida.shared.ApiException;
import java.util.Set;

/** Min 12 characters and not a common password (section 10). */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON = Set.of(
            "password1234", "123456789012", "qwertyuiop12", "contrasena123", "contraseña123", "administrador",
            "passw0rd1234", "iloveyou1234", "welcome12345", "colombia12345", "123456789abc", "abcdefghijkl",
            "motocicleta1", "taller123456", "nitida123456", "password12345", "qwerty123456", "letmein12345");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw ApiException.unprocessable("WEAK_PASSWORD", "La contraseña debe tener entre 12 y 128 caracteres");
        }
        if (COMMON.contains(password.toLowerCase()) || password.chars().distinct().count() < 5) {
            throw ApiException.unprocessable("WEAK_PASSWORD", "Esa contraseña es demasiado común, elija otra");
        }
    }
}
