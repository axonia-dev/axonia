package dev.axonia.selfhosted.application.port.out;

import java.util.Optional;

import dev.axonia.selfhosted.domain.Founded;

/** The installation's founding, written once, at the first start. */
public interface FoundingLedger {

    /** Holds the founding lock until the current transaction ends, so replicas starting together found once. */
    void serializeFounding();

    Optional<Founded> find();

    void record(Founded founded);
}
