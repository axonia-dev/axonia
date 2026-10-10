package dev.axonia.shared.api.persistence;

import java.time.Instant;

public interface Entity {

    Long getId();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    Long getVersion();
}
