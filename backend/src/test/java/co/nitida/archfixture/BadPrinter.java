package co.nitida.archfixture;

/** Deliberate violations: prove each ArchUnit rule fails when the code breaks it. Excluded from the real scan. */
public class BadPrinter {
    public void leak(String secret) {
        System.out.println(secret);
    }

    public int token() {
        return new java.util.Random().nextInt();
    }
}
