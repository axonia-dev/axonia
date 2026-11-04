package dev.axonia.events.application;

import jakarta.validation.constraints.NotNull;

/**
 * {@code wrapUp} is the organizer's answer about a held session's wrap-up, given at the close: {@code true} releases it
 * after the community's grace period, {@code false} suppresses it for good, and {@code null} leaves it to
 * {@link WrapUpReleasePolicy}'s default. A session that did not take place has no wrap-up, so it is ignored then.
 */
public record CloseSessionRequest(@NotNull Boolean held, Boolean wrapUp) {
}
