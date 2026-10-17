package dev.axonia.recognition.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.ImpactVisits;
import dev.axonia.recognition.application.port.out.NewLoveQuery;
import dev.axonia.recognition.application.port.out.NewLoveTallies;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.domain.NewLoveTally;
import dev.axonia.recognition.domain.Recognition;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The loves My impact replays on arrival, and the marker that ends a replay once it has played.
 * <p>
 * Two edge cases are accepted rather than fixed: a love stamped before a read's {@code asOf} but committed after its
 * snapshot is never replayed (its count stays right), and clock skew between replicas can refuse a valid mark-seen as
 * {@code FUTURE_AS_OF}, so the replay plays again. Both are cosmetic; neither calls for SQL {@code now()}.
 */
@Service
@RequiredArgsConstructor
public class ImpactReplayService {

    static final Duration WINDOW = Duration.ofDays(30);

    private final CreditedRecognitions creditedRecognitions;
    private final NewLoveTallies newLoveTallies;
    private final ImpactVisits impactVisits;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    @Transactional(readOnly = true)
    public NewLoves newLoves(Optional<Long> communityId) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var asOf = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var credited = creditedRecognitions.of(organizationId, userId).stream()
                .filter(rc -> communityId.isEmpty() || communityId.get().equals(rc.communityId())).toList();
        var tallies = new ArrayList<NewLoveTally>();
        for (var type : ContributionType.values()) {
            var ids = credited.stream().filter(rc -> rc.contributionType() == type).map(Recognition::contributionId)
                    .toList();
            if (!ids.isEmpty()) {
                tallies.addAll(newLoveTallies.since(NewLoveQuery.builder().organizationId(organizationId).userId(userId)
                        .contributionType(type).contributionIds(ids).floor(asOf.minus(WINDOW)).asOf(asOf).build()));
            }
        }
        return NewLoves.builder().asOf(asOf).capped(tallies.stream().anyMatch(NewLoveTally::capped))
                .items(tallies.stream().map(ImpactReplayService::toItem).toList()).build();
    }

    @Transactional
    public void markSeen(MarkSeenRequest request) {

        var asOf = request.asOf().truncatedTo(ChronoUnit.MILLIS);
        if (asOf.isAfter(clock.instant())) {
            throw new ValidationException("FUTURE_AS_OF", "asOf cannot be in the future");
        }
        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var communityIds = communitiesToMark(organizationId, userId, request.communityId());
        if (!communityIds.isEmpty()) {
            impactVisits.markSeen(organizationId, userId, communityIds, asOf);
        }
    }

    private List<Long> communitiesToMark(String organizationId, String userId, Long communityId) {

        if (communityId != null) {
            return List.of(communityId);
        }
        return creditedRecognitions.of(organizationId, userId).stream().map(Recognition::communityId).distinct()
                .toList();
    }

    private static NewLoveItem toItem(NewLoveTally tally) {

        return new NewLoveItem(tally.contributionType(), tally.contributionId(), tally.newLoves());
    }
}
