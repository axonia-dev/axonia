package dev.axonia.events.application;

import java.time.Instant;

/** A null {@code releaseAt} permanently suppresses the wrap-up for this session. */
public record WrapUpReleaseRequest(Instant releaseAt) {
}
