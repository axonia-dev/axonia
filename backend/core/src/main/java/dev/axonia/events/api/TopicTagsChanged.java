package dev.axonia.events.api;

import java.util.List;

/** A topic's subject or speakers changed: every speaker is credited with offering its tags, none means no offer. */
public record TopicTagsChanged(Long topicId, Long communityId, List<String> speakerUserIds, List<String> tags) {
}
