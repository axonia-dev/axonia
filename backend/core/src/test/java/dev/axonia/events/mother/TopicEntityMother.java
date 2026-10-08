package dev.axonia.events.mother;

import java.util.Set;

import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicStatus;
import dev.axonia.events.internal.TopicEntity;
import dev.axonia.events.internal.TopicTagRef;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TopicEntityMother {

    public TopicEntity.TopicEntityBuilder any() {

        return TopicEntity.builder().organizationId("default").communityId(1L).kind(TopicKind.REQUEST)
                .title("Intro to event sourcing").description("Would love a session").proposerUserId("alice")
                .status(TopicStatus.PROPOSED).tags(Set.of(TopicTagRef.builder().tag("kafka").build()));
    }
}
