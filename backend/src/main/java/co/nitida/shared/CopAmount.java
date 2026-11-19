package co.nitida.shared;

/** Colombian pesos as whole numbers (RN-44). A line is 0..50.000.000; totals may exceed that. */
public record CopAmount(long value) implements Comparable<CopAmount> {
    public static final long LINE_MAX = 50_000_000L;
    public static final CopAmount ZERO = new CopAmount(0);

    public CopAmount {
        if (value < 0) throw ApiException.unprocessable("INVALID_AMOUNT", "El valor no puede ser negativo");
    }

    public static CopAmount line(long value) {
        if (value < 0 || value > LINE_MAX) {
            throw ApiException.unprocessable("INVALID_AMOUNT", "El valor debe estar entre 0 y 50.000.000");
        }
        return new CopAmount(value);
    }

    public CopAmount plus(CopAmount other) {
        return new CopAmount(Math.addExact(value, other.value));
    }

    public CopAmount times(int quantity) {
        return new CopAmount(Math.multiplyExact(value, (long) quantity));
    }

    @Override
    public int compareTo(CopAmount o) {
        return Long.compare(value, o.value);
    }
}
