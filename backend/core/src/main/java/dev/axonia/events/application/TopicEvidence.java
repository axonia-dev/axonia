package dev.axonia.events.application;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.axonia.events.api.TopicTagsChanged;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicTag;
import lombok.RequiredArgsConstructor;

/**
 * Tells the expertise graph who offers a topic, with its tags as they are now. Every write that changes a topic's tags
 * or speakers publishes through here, so the offer evidence follows the speakers rather than the creator.
 */
@Component
@RequiredArgsConstructor
public class TopicEvidence {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(TopicRecord topic, List<String> speakerUserIds) {

        eventPublisher.publishEvent(new TopicTagsChanged(topic.getId(), topic.getCommunityId(),
                List.copyOf(speakerUserIds), TagRefs.sorted(topic.getTags(), TopicTag::getTag)));
    }
}
