package dev.axonia.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Record a slot that these people presented. {@code presenters} holds free-text references (a display name or an email)
 * as the source tool spells them; each is resolved against the community's members. An empty or null list records an
 * open slot, which is also the fallback when a name cannot be placed.
 *
 * <p>
 * {@code tags} say what the talk was about. A recorded slot was never proposed, so it has no topic to inherit a subject
 * from: without them it teaches the expertise graph nothing.
 */
public record RecordSlotRequest(@NotNull String title, @NotNull @Positive Integer durationMinutes, String notes,
        List<String> presenters, List<String> tags) {

    public RecordSlotRequest {
        // Neither list uses List.copyOf: a JSON array can carry a null element, and both cleaning rules are written to
        // drop it rather than to fail on it (PresenterResolver skips a blank or null reference; Tags.clean skips a null
        // tag). List.copyOf would NPE instead, turning the dirty migration data this endpoint exists to accept into a
        // 500 - and a null presenter is exactly the shape of an exported row whose speaker column was empty.
        presenters = presenters == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(presenters));
        tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    }
}
