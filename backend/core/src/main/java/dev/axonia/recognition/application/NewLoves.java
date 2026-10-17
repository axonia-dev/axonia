package dev.axonia.recognition.application;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

/** Loves received since the member last viewed My impact, up to {@code asOf}; capped when the window cut them short. */
@Builder
public record NewLoves(Instant asOf, boolean capped, List<NewLoveItem> items) {

    public NewLoves {
        items = List.copyOf(items);
    }
}
