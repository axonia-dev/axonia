package dev.axonia.communication.api;

public enum OutboxStatus {
    PENDING, SENDING, SENT, FAILED, SUPPRESSED, ABANDONED
}
