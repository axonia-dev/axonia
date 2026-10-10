package dev.axonia.events.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@ToString
public class SectionTypeRecord {

    private Long id;
    private String organizationId;
    private String label;
    private boolean expectsPresenter;
    private String icon;
    private String accentColor;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
