package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.events.domain.CadenceUnit;
import dev.axonia.events.domain.SeriesStatus;
import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
@Table("event_series")
public class SeriesEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String title;
    private int cadenceCount;
    private CadenceUnit cadenceUnit;
    private LocalDate startsOn;
    private LocalTime preferredTime;
    private String timeZone;
    private Integer durationMinutes;
    private SeriesStatus status;
    private String location;
    private String joinUrl;
    private String joiningDetails;

    @MappedCollection(idColumn = "series_id", keyColumn = "position")
    private List<SectionTypeRef> defaultStructure;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
