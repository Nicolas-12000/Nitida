package co.nitida.intake.domain;

/** RN-31: the only allowed reasons to close an intake without an order. */
public enum DiscardReason {
    CONSULTATION_ONLY, CUSTOMER_WITHDREW, ENTRY_ERROR
}
