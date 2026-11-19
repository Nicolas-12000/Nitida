package co.nitida.catalog;

/** RN-91, from lowest to highest. The UI must never call SCANNED "verified". */
public enum EvidenceLevel {
    MANUAL, SCANNED, UNIQUE_SERIAL, AUTHENTICITY_VERIFIED;

    /** The highest level the present evidence allows. */
    public static EvidenceLevel compute(boolean validProductCode, boolean acceptedSerial, boolean authenticityWithPhoto) {
        if (authenticityWithPhoto) return AUTHENTICITY_VERIFIED;
        if (acceptedSerial) return UNIQUE_SERIAL;
        if (validProductCode) return SCANNED;
        return MANUAL;
    }
}
