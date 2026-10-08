package dev.axonia.events;

import java.time.Instant;

import org.springframework.data.domain.Pageable;

import lombok.Builder;

/**
 * What one retrospective read of a community's sessions asks for: which community, how much of the past, which
 * statuses, and how much of it to return.
 *
 * <p>
 * A parameter object rather than five arguments, which is where PMD's {@code ExcessiveParameterList} trips and where a
 * caller stops being able to tell {@code from} from {@code to} at the call site.
 *
 * <p>
 * {@code status} narrows a window that is otherwise "every non-cancelled occurrence whose hour has passed" - a set that
 * includes occurrences the community skipped and ones nobody ever confirmed either way. A caller that speaks about what
 * a community actually did asks for {@link SessionStatus#HELD}; a null asks for the whole window, unchanged.
 */
@Builder
public record PastSessionsRequest(String slug, Instant from, Instant to, SessionStatus status, Pageable pageable) {
}
