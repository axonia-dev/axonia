package dev.axonia.knowledge.api;

import java.time.Instant;

import lombok.Builder;

/** Query-string shape of the artifact library search, bound by Spring from the request parameters. */
@Builder
public record ArtifactSearchRequest(String q, Long type, String tag, String contributor, Instant from, Instant to,
        Long session, String sort) {

    public ArtifactFilter toFilter() {

        return ArtifactFilter.builder().q(q).typeId(type).tag(tag).contributor(contributor).from(from).to(to)
                .session(session).sort(sort).build();
    }
}
