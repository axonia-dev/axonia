package dev.axonia.events;

import java.util.List;

import lombok.Builder;

/**
 * Which presenter references placed and which did not, carrying the caller's original query strings back so it can
 * honestly report coverage ("7 of 9 presenters matched") rather than failing the import or inventing an attribution.
 */
@Builder
public record PresenterResolution(List<String> assigned, List<String> unmatched, List<String> ambiguous) {

    public static PresenterResolution empty() {

        return new PresenterResolution(List.of(), List.of(), List.of());
    }
}
