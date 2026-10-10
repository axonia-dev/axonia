package dev.axonia.events.adapter.in.web;

import java.time.Instant;

import org.springframework.data.domain.Pageable;

import dev.axonia.events.api.PastSessionsRequest;
import dev.axonia.events.api.SessionStatus;
import lombok.Builder;

/**
 * Query-string shape of a community's retrospective session read, bound by Spring from the request parameters.
 *
 * <p>
 * Separate from {@link PastSessionsRequest} because the two say different things: this one is whatever the caller put
 * on the URL, the other is a complete question with the community and the page in it.
 */
@Builder
public record PastSessionsFilter(Instant from, Instant to, SessionStatus status) {

    PastSessionsRequest toRequest(String slug, Pageable pageable) {

        return PastSessionsRequest.builder().slug(slug).from(from).to(to).status(status).pageable(pageable).build();
    }
}
