package co.nitida.history;

/** VALID, INVALID(seq, reason) or UNVERIFIABLE(reason). Anything unexpected is UNVERIFIABLE, never VALID. */
public record VerificationResult(Status status, Long seq, String reason) {
    public enum Status { VALID, INVALID, UNVERIFIABLE }

    public static VerificationResult valid() { return new VerificationResult(Status.VALID, null, null); }

    public static VerificationResult invalid(long seq, String reason) { return new VerificationResult(Status.INVALID, seq, reason); }

    public static VerificationResult unverifiable(String reason) { return new VerificationResult(Status.UNVERIFIABLE, null, reason); }

    public boolean isValid() { return status == Status.VALID; }
}
