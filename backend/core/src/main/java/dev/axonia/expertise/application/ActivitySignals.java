package dev.axonia.expertise.application;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.SignalProjection;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SourceType;
import lombok.RequiredArgsConstructor;

/**
 * Keeps the expertise signals in step with the activity they are projected from, in the caller's Organization.
 *
 * <p>
 * The upstream activity events carry no {@code organizationId}, so the Organization is read from
 * {@link OrganizationContext}, the request's token. That holds while those events are dispatched synchronously on the
 * request thread; if they ever go async, the Organization must travel in the event instead.
 */
@Service
@RequiredArgsConstructor
public class ActivitySignals {

    private final SignalProjection signalProjection;
    private final OrganizationContext organizationContext;

    /** The signals a source carries now replace whatever it carried before. */
    public void replaceSource(long communityId, SourceType sourceType, Long sourceId, List<SignalRow> rows) {

        signalProjection.replaceSource(
                new SignalSource(organizationContext.organizationId(), communityId, sourceType, sourceId), rows);
    }

    /** A source that no longer stands drops every signal it carried. */
    public void deleteSource(SourceType sourceType, Long sourceId) {

        signalProjection.deleteSource(organizationContext.organizationId(), sourceType, sourceId);
    }
}
