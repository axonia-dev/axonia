package dev.axonia.events.application;

import jakarta.validation.constraints.NotNull;

public record CloseSessionRequest(@NotNull Boolean held) {
}
