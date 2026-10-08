package dev.axonia.events;

import jakarta.validation.constraints.NotNull;

public record CloseSessionRequest(@NotNull Boolean held) {
}
