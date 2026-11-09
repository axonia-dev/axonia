package dev.axonia.events.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.events.api.TopicStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class TopicRecord {

    private Long id;
    private String organizationId;
    private Long communityId;
    private String title;
    private String description;
    private String proposerUserId;
    private TopicStatus status;
    private Set<TopicTag> tags;

    /** Who will present it, while it is on the backlog. A scheduled or done topic reads its slot's presenters. */
    private Set<TopicSpeaker> speakers;

    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    /** The backlog speakers, sorted and distinct. A scheduled or done topic has none here: they are its slot's. */
    public List<String> speakerUserIds() {

        return Objects.requireNonNullElse(speakers, Set.<TopicSpeaker>of()).stream().map(TopicSpeaker::getUserId)
                .distinct().sorted().toList();
    }

    public boolean hasSpeaker(String userId) {

        return speakerUserIds().contains(userId);
    }

    public TopicRecord withSpeakers(List<String> userIds) {

        return toBuilder().speakers(userIds.stream().distinct().map(u -> TopicSpeaker.builder().userId(u).build())
                .collect(Collectors.toCollection(LinkedHashSet::new))).build();
    }

    public TopicRecord withSpeaker(String userId) {

        var userIds = new ArrayList<>(speakerUserIds());
        userIds.add(userId);
        return withSpeakers(userIds);
    }

    public TopicRecord withoutSpeaker(String userId) {

        return withSpeakers(speakerUserIds().stream().filter(u -> !u.equals(userId)).toList());
    }
}
