package co.nitida.shared;


import java.util.regex.Pattern;

public final class Emails {
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}\\.[^@\\s]{2,}$");

    private Emails() {}

    public static String normalize(String raw) {
        String e = raw == null ? "" : raw.strip().toLowerCase();
        if (e.length() > 254 || !SHAPE.matcher(e).matches()) {
            throw ApiException.unprocessable("INVALID_EMAIL", "El correo no es válido");
        }
        return e;
    }
}
