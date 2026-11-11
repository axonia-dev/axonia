package dev.axonia.events.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FeedbackAnswer(@Min(1) @Max(10) int enjoyed, @Min(1) @Max(10) int learned) {
}
