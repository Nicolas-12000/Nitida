package co.nitida.shared;

/** Odometer reading in km (RN-45): 0..999.999. */
public record Mileage(int km) implements Comparable<Mileage> {
    public static final int MAX = 999_999;

    public Mileage {
        if (km < 0 || km > MAX) {
            throw ApiException.unprocessable("INVALID_MILEAGE", "El kilometraje debe estar entre 0 y 999.999");
        }
    }

    /** Equal or higher than the last reading is valid; lower is a regression. */
    public void validateAgainst(Mileage last) {
        if (last != null && km < last.km) {
            throw ApiException.unprocessable("MILEAGE_REGRESSION",
                    "El kilometraje es menor al último registrado para esta moto");
        }
    }

    @Override
    public int compareTo(Mileage o) {
        return Integer.compare(km, o.km);
    }
}
