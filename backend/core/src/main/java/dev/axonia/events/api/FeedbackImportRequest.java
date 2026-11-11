package dev.axonia.events.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Builder;

/**
 * {@code scale} is the one the old form used, 5 or 10, and each answer keeps its values as given on it. {@code source}
 * names where the answers came from. Both are read only when {@code answers} holds something to store.
 */
@Builder
public record FeedbackImportRequest(int scale, String source, List<Answer> answers) {

    /** A missing list stays null, which the import refuses; a copy rather than List.copyOf keeps a null answer. */
    public FeedbackImportRequest {
        answers = answers == null ? null : Collections.unmodifiableList(new ArrayList<>(answers));
    }

    /** One anonymous respondent: either question may be skipped, never both. */
    public record Answer(Integer enjoyed, Integer learned) {
    }
}
