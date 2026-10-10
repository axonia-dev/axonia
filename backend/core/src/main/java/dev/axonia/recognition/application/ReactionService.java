package dev.axonia.recognition.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.application.port.out.ReactionStore;
import dev.axonia.recognition.domain.ReactionDefinition;
import dev.axonia.recognition.domain.ReactionKind;
import dev.axonia.recognition.domain.ReactionPlacement;
import dev.axonia.recognition.domain.ReactionPlacementKey;
import dev.axonia.recognition.domain.ReactionRef;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * Placing and withdrawing a reaction, and tallying what a contribution has drawn.
 *
 * <p>
 * <strong>A reaction is not a love and never becomes one.</strong> It creates no {@code recognized_contribution}, it
 * credits nobody, it publishes no event, it reaches neither the health aggregate nor an expertise tier, and it never
 * appears on My impact. Love remains the only gesture that counts. A reaction placed on one's own contribution is
 * allowed for exactly that reason: there is no credit for it to flow to.
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionStore reactions;

    private final ReactionCatalogService catalog;

    private final CommunityAccess communityAccess;

    private final SlotSourceAccess slotSourceAccess;

    private final ArtifactSourceAccess artifactSourceAccess;

    private final CurrentUser currentUser;

    private final OrganizationContext organizationContext;

    @Transactional
    public ReactionTally react(ReactionRequest request) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var ref = request.ref();
        var source = resolveSource(organizationId, request.contributionType(), request.contributionId());
        if (!communityAccess.isMember(source.communityId(), userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var definition = requireUsable(ref);
        if (!reactions
                .place(ReactionPlacement.builder().organizationId(organizationId).communityId(source.communityId())
                        .contributionType(request.contributionType()).contributionId(request.contributionId())
                        .reactorUserId(userId).emoji(ref.emoji()).definitionId(ref.definitionId()).build())) {
            throw new ConflictException("ALREADY_REACTED", "This reaction is already given");
        }
        return tallyOf(ref, definition, request.contributionType(), request.contributionId());
    }

    @Transactional
    public void unreact(ContributionType contributionType, Long contributionId, ReactionRef ref) {

        var key = ReactionPlacementKey.builder().organizationId(organizationContext.organizationId())
                .contributionType(contributionType).contributionId(contributionId).reactorUserId(currentUser.userId())
                .emoji(ref.emoji()).definitionId(ref.definitionId()).build();
        var removed = reactions.remove(key);
        if (removed == 0) {
            throw new NotFoundException("No reaction to remove");
        }
    }

    /**
     * Ordered by count descending, then by first placement ascending, so the strip does not reshuffle on every render
     * merely because two reactions are tied. The tie-break is computed explicitly from each placement's
     * {@code createdAt} rather than left to rely on the repository's incidental row order, which Spring Data JDBC does
     * not guarantee absent an explicit {@code ORDER BY}.
     */
    @Transactional(readOnly = true)
    public List<ReactionTally> tally(ContributionType contributionType, Long contributionId) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var placements = reactions.placementsOn(organizationId, contributionType, contributionId);
        var definitions = definitionsById(placements);

        Map<ReactionRef, List<ReactionPlacement>> grouped = new LinkedHashMap<>();
        for (var placement : placements) {
            grouped.computeIfAbsent(refOf(placement), k -> new ArrayList<>()).add(placement);
        }
        return grouped.values().stream()
                .sorted(Comparator.<List<ReactionPlacement>>comparingInt(List::size).reversed()
                        .thenComparing(ReactionService::firstPlacedAt))
                .map(group -> toTally(group, definitions, userId)).toList();
    }

    private ReactionTally toTally(List<ReactionPlacement> placements, Map<Long, ReactionDefinition> definitions,
            String userId) {

        var ref = refOf(placements.get(0));
        var definition = ref.kind() == ReactionKind.CUSTOM ? definitions.get(ref.definitionId()) : null;
        return buildTally(ref, definition, placements, userId);
    }

    /**
     * {@code organizationId} and {@code userId} are read from this service's own {@code organizationContext} and
     * {@code currentUser} fields rather than accepted as parameters: {@link #react(ReactionRequest)} already reads both
     * for its own purposes earlier in the same request, so passing them again here would only be forwarding what this
     * method can reach directly.
     */
    private ReactionTally tallyOf(ReactionRef ref, ReactionDefinition definition, ContributionType contributionType,
            Long contributionId) {

        var placements = reactions.placementsOn(organizationContext.organizationId(), contributionType, contributionId)
                .stream().filter(p -> refOf(p).equals(ref)).toList();
        return buildTally(ref, definition, placements, currentUser.userId());
    }

    private static ReactionTally buildTally(ReactionRef ref, ReactionDefinition definition,
            List<ReactionPlacement> placements, String userId) {

        return ReactionTally.builder().kind(ref.kind()).emoji(ref.emoji()).definitionId(ref.definitionId())
                .name(definition == null ? null : definition.name())
                .imageUrl(definition == null ? null : definition.imageUrl()).count(placements.size())
                .viewerHasReacted(placements.stream().anyMatch(p -> p.reactorUserId().equals(userId))).build();
    }

    /** A pending definition is not usable, including by the person who proposed it. */
    private ReactionDefinition requireUsable(ReactionRef ref) {

        if (ref.kind() == ReactionKind.EMOJI) {
            return null;
        }
        return catalog.approved().stream().filter(d -> d.id().equals(ref.definitionId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Reaction not available: " + ref.definitionId()));
    }

    /**
     * A contribution carrying only standard emoji needs no catalog at all, and most carry only standard emoji. The
     * reaction strip renders once per slot and once per artifact card, so a session page asks for this many times over;
     * reading the Organization's whole approved vocabulary each time to resolve nothing is a cost paid for an empty
     * answer.
     */
    private Map<Long, ReactionDefinition> definitionsById(List<ReactionPlacement> placements) {

        if (placements.stream().allMatch(p -> p.definitionId() == null)) {
            return Map.of();
        }
        var byId = new HashMap<Long, ReactionDefinition>();
        catalog.approved().forEach(d -> byId.put(d.id(), d));
        return byId;
    }

    private static ReactionRef refOf(ReactionPlacement e) {

        return e.emoji() == null
                ? new ReactionRef(ReactionKind.CUSTOM, null, e.definitionId())
                : new ReactionRef(ReactionKind.EMOJI, e.emoji(), null);
    }

    private static Instant firstPlacedAt(List<ReactionPlacement> group) {

        return group.stream().map(ReactionPlacement::createdAt).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }

    private ContributionSource resolveSource(String organizationId, ContributionType type, Long contributionId) {

        var source = switch (type) {
            case ARTIFACT_CAPTURED -> artifactSourceAccess.artifactSource(organizationId, contributionId);
            case SLOT_PRESENTED -> slotSourceAccess.slotContribution(organizationId, contributionId);
        };
        return source.orElseThrow(() -> new NotFoundException("Contribution not found: " + contributionId));
    }
}
