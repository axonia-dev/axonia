package dev.axonia.events.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("community_session")
public class SessionEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long seriesId;
    private Long communityId;
    private String status;
    private Instant scheduledAt;
    private boolean hasTime;

    /**
     * A one-off's own clock, in the zone it was typed in. Null for a session of a series, which reads its series' zone:
     * one occurrence must never disagree with the rhythm it belongs to about what time it is.
     */
    private String timeZone;

    private LocalDate occurrenceDate;
    private String title;
    private Integer durationMinutes;
    private String facilitatorUserId;
    private String notes;
    private String recordingUrl;
    private String location;
    private String joinUrl;
    private String joiningDetails;
    private Instant closedAt;
    private Instant wrapUpReleaseAt;
    private Instant upcomingReleaseAt;

    @MappedCollection(idColumn = "session_id", keyColumn = "position")
    private List<SectionEntity> sections;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
