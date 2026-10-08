package dev.axonia.events.internal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.ConflictException;

/**
 * What a talk is about. A slot takes its subject from the topic it was filled from, or carries its own tags when it was
 * never proposed: it has one or the other, never both.
 *
 * <p>
 * Two subjects and no rule for choosing between them is a caller error, not something to resolve by dropping one:
 * silently discarding a subject the caller supplied is exactly how the expertise graph learns the wrong thing.
 */
public final class SlotSubject {

    private SlotSubject() {
    }

    /**
     * The slot's own tags, cleaned, for a slot that carries no topic. Refuses a caller that supplied both, before
     * anything is written.
     */
    public static Set<String> own(Long topicId, Collection<String> tags) {

        if (topicId != null && tags != null && !tags.isEmpty()) {
            throw new ConflictException("SLOT_TOPIC_AND_TAGS",
                    "A slot filled from a topic takes its subject from the topic");
        }
        return Tags.clean(tags);
    }

    /**
     * The slot's own tags after an edit. Omitted tags ({@code null}) keep the subject the slot already has: an edit
     * that only fixes a typo in the title sends no tags, and erasing what the talk was about would silently cost its
     * presenters their expertise. An explicit empty list still clears the subject.
     *
     * <p>
     * The invariant is applied to the <em>effective</em> tags, not to the requested ones. Checking the request would
     * leave a hole: attaching a topic to a slot that already carries its own tags, while omitting tags, would keep both
     * and produce exactly the two-subject slot the invariant exists to prevent.
     */
    public static Set<String> edited(Long topicId, Collection<String> requested, Collection<String> current) {

        return own(topicId, requested == null ? current : requested);
    }

    /**
     * {@link Tags#clean} already reduced these to distinct slugs, which matters: {@link SlotTagRef} has no
     * equals/hashCode, so a set of freshly-built refs would not catch a duplicate and would violate the
     * {@code event_slot_tag} primary key.
     */
    public static Set<SlotTagRef> refs(Set<String> tags) {

        return tags.stream().map(t -> SlotTagRef.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
