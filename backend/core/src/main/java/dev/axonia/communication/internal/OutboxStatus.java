package dev.axonia.communication.internal;

enum OutboxStatus {
    PENDING, SENDING, SENT, FAILED, SUPPRESSED, ABANDONED
}
