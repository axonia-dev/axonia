package dev.axonia.learning.domain;

import java.util.List;

import lombok.Builder;

/** One member's new openness on an area, told to the learners of it in the community. */
@Builder
public record OpenDoorNotice(long communityId, String communitySlug, String communityName, String declarerId,
        String declarerName, String area, List<String> learnerIds) {
}
