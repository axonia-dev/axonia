package dev.axonia.organization;

/** ACTIVE organizations operate normally; SUSPENDED and CLOSED restrict access while the row is retained. */
public enum OrganizationLifecycleState {
    ACTIVE, SUSPENDED, CLOSED
}
