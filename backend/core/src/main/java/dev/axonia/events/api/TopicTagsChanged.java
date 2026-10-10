package dev.axonia.events.api;

import java.util.List;

public record TopicTagsChanged(Long topicId, Long communityId, String proposerUserId, TopicKind kind,
        List<String> tags) {
}
