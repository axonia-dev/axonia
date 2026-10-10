package dev.axonia.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class SeriesRecord {

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
    private List<PlannedSection> defaultStructure;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
