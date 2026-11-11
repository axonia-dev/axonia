package dev.axonia.events.domain;

/** One imported respondent's answer: either question may be skipped, never both. */
public record ImportedAnswer(Integer enjoyed, Integer learned) {

    public ImportedAnswer {

        if (enjoyed == null && learned == null) {
            throw new IllegalArgumentException("An imported answer needs at least one value");
        }
    }
}
