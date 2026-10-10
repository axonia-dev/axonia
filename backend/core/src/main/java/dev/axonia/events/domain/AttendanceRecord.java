package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class AttendanceRecord {

    private Long id;
    private String organizationId;
    private Long sessionId;
    private String userId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
