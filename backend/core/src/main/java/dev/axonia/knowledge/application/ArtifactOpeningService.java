package dev.axonia.knowledge.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.application.port.out.ArtifactOpeningLog;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.RecordOpeningCommand;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Records that somebody followed an artifact's link out to the content itself.
 *
 * <p>
 * Membership is checked rather than assumed: without it anybody holding an artifact id could inflate another
 * community's number, and the whole point of the figure is that a leader can say it out loud.
 *
 * <p>
 * A contributor opening their own artifact IS recorded here and excluded later, in the aggregate. Excluding it at the
 * write would leave a hole in that member's own data export, and the export is what the product offers in place of an
 * opt-out.
 *
 * <p>
 * {@code openedOn} is the UTC date of the same instant the row is stamped with. It is never rendered to anybody and
 * exists only to collapse repeats, so the arbitrariness of a UTC day boundary is harmless. It must not be "corrected"
 * to a community's zone later: that would silently change what the number counts.
 */
@Service
@RequiredArgsConstructor
public class ArtifactOpeningService {

    private final ArtifactOpeningLog writer;
    private final ArtifactStore artifacts;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    @Transactional
    public void record(Long artifactId) {

        var organizationId = organizationContext.organizationId();
        var artifact = artifacts.find(organizationId, artifactId)
                .orElseThrow(() -> new NotFoundException("Artifact not found"));
        var userId = currentUser.userId();
        if (!communityAccess.isMember(artifact.communityId(), userId)) {
            throw new ForbiddenException("Only a member of the community may open its artifacts");
        }
        var now = clock.instant();
        writer.record(RecordOpeningCommand.builder().organizationId(organizationId).communityId(artifact.communityId())
                .artifactId(artifactId).memberUserId(userId).createdAt(now)
                .openedOn(LocalDate.ofInstant(now, ZoneOffset.UTC)).build());
    }
}
