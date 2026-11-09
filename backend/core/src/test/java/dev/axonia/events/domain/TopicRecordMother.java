package dev.axonia.events.domain;

import java.util.Set;

import dev.axonia.events.api.TopicStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicRecordMother {

    public TopicRecord.TopicRecordBuilder any() {

        return TopicRecord.builder().organizationId("default").communityId(1L).title("Intro to event sourcing")
                .description("Would love a session").proposerUserId("alice").status(TopicStatus.PROPOSED)
                .tags(Set.of(TopicTag.builder().tag("kafka").build()));
    }
}
