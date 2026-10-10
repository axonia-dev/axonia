package dev.axonia.events.adapter.out.persistence;

import java.util.Set;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicEntityMother {

    public TopicEntity.TopicEntityBuilder any() {

        return TopicEntity.builder().organizationId("default").communityId(1L).kind(TopicKind.REQUEST)
                .title("Intro to event sourcing").description("Would love a session").proposerUserId("alice")
                .status(TopicStatus.PROPOSED).tags(Set.of(TopicTagRef.builder().tag("kafka").build()));
    }
}
