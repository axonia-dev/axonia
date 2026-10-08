package dev.axonia.recognition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.ValidationException;
import dev.axonia.recognition.internal.LoveEntity;
import dev.axonia.recognition.internal.LoveRepository;
import dev.axonia.recognition.internal.RecognizedContributionEntity;
import dev.axonia.recognition.internal.RecognizedContributionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecognitionService {

    private final RecognizedContributionRepository recognizedContributionRepository;
    private final LoveRepository loveRepository;
    private final CommunityAccess communityAccess;
    private final SlotSourceAccess slotSourceAccess;
    private final ArtifactSourceAccess artifactSourceAccess;
    private final ArtifactTypeAccess artifactTypeAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher events;

    @Transactional
    public Love love(LoveRequest request) {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var source = resolveSource(organizationId, request);
        if (source.contributorUserIds().isEmpty()) {
            throw new ValidationException("NO_CONTRIBUTORS", "This contribution has no one to credit");
        }
        if (source.contributorUserIds().contains(userId)) {
            throw new ConflictException("NO_SELF_LOVE",
                    "A contribution cannot be loved by one of its own contributors");
        }
        if (!communityAccess.isMember(source.communityId(), userId)) {
            throw new ForbiddenException("Community membership required");
        }
        var rc = findOrCreate(organizationId, request, source);
        try {
            var saved = loveRepository.save(LoveEntity.builder().organizationId(organizationId)
                    .recognizedContributionId(rc.getId()).loverUserId(userId).build());
            events.publishEvent(
                    new ContributionLoved(rc.getContributionType(), rc.getContributionId(), rc.getCommunityId()));
            return toLove(saved, rc);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("ALREADY_LOVED", "You have already loved this contribution");
        }
    }

    @Transactional
    public void unlove(ContributionType contributionType, Long contributionId) {

        var organizationId = organizationContext.organizationId();
        var rc = recognizedContributionRepository
                .findByOrganizationIdAndContributionTypeAndContributionId(organizationId, contributionType,
                        contributionId)
                .orElseThrow(() -> new NotFoundException("No love to remove"));
        var love = loveRepository.findByOrganizationIdAndRecognizedContributionIdAndLoverUserId(organizationId,
                rc.getId(), currentUser.userId()).orElseThrow(() -> new NotFoundException("No love to remove"));
        loveRepository.delete(love);
        events.publishEvent(
                new ContributionUnloved(rc.getContributionType(), rc.getContributionId(), rc.getCommunityId()));
    }

    @Transactional(readOnly = true)
    public LoveSummary summary(ContributionType contributionType, Long contributionId) {

        var organizationId = organizationContext.organizationId();
        var rc = recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId(
                organizationId, contributionType, contributionId);
        if (rc.isEmpty()) {
            return LoveSummary.builder().count(0).viewerHasLoved(false).build();
        }
        var loves = loveRepository.findByOrganizationIdAndRecognizedContributionId(organizationId, rc.get().getId());
        var userId = currentUser.userId();
        return LoveSummary.builder().count(loves.size())
                .viewerHasLoved(loves.stream().anyMatch(l -> l.getLoverUserId().equals(userId))).build();
    }

    @Transactional(readOnly = true)
    public List<RecognizedContribution> myContributions() {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var entities = new ArrayList<RecognizedContributionEntity>();
        entities.addAll(recognizedOf(organizationId, ContributionType.SLOT_PRESENTED,
                slotSourceAccess.slotsPresentedBy(organizationId, userId)));
        entities.addAll(recognizedOf(organizationId, ContributionType.ARTIFACT_CAPTURED,
                artifactSourceAccess.artifactsCapturedBy(organizationId, userId)));
        entities.sort(Comparator.comparing(RecognizedContributionEntity::getOccurredAt).reversed());
        var summaries = communityAccess
                .summaryById(entities.stream().map(RecognizedContributionEntity::getCommunityId).toList());
        var slotSources = slotSourceAccess.slotSources(idsOfType(entities, ContributionType.SLOT_PRESENTED));
        var artifactBadges = artifactTypeAccess.artifactTypes(idsOfType(entities, ContributionType.ARTIFACT_CAPTURED));
        var catalogs = new ContributionCatalogs(slotSources, artifactBadges);
        return entities.stream()
                .map(rc -> toRecognizedContribution(rc, summaries.get(rc.getCommunityId()), catalogs, userId)).toList();
    }

    /** Groups the lookup tables {@link #toRecognizedContribution} needs, to stay under the parameter-count limit. */
    private record ContributionCatalogs(Map<Long, SlotSource> slotSources, Map<Long, ArtifactBadge> artifactBadges) {
    }

    private List<RecognizedContributionEntity> recognizedOf(String organizationId, ContributionType type,
            List<Long> contributionIds) {

        if (contributionIds.isEmpty()) {
            return List.of();
        }
        return recognizedContributionRepository
                .findByOrganizationIdAndContributionTypeAndContributionIdIn(organizationId, type, contributionIds);
    }

    private static List<Long> idsOfType(List<RecognizedContributionEntity> entities, ContributionType type) {

        return entities.stream().filter(e -> e.getContributionType() == type)
                .map(RecognizedContributionEntity::getContributionId).toList();
    }

    private ContributionSource resolveSource(String organizationId, LoveRequest r) {

        var source = switch (r.contributionType()) {
            case ARTIFACT_CAPTURED -> artifactSourceAccess.artifactSource(organizationId, r.contributionId());
            case SLOT_PRESENTED -> slotSourceAccess.slotContribution(organizationId, r.contributionId());
        };
        return source.orElseThrow(() -> new NotFoundException("Contribution not found: " + r.contributionId()));
    }

    private RecognizedContributionEntity findOrCreate(String organizationId, LoveRequest r, ContributionSource source) {

        return recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId(organizationId,
                r.contributionType(), r.contributionId()).orElseGet(() -> {
                    try {
                        return recognizedContributionRepository.save(RecognizedContributionEntity.builder()
                                .organizationId(organizationId).contributionType(r.contributionType())
                                .contributionId(r.contributionId()).communityId(source.communityId())
                                .title(source.title()).occurredAt(source.occurredAt()).build());
                    } catch (DataIntegrityViolationException ex) {
                        return recognizedContributionRepository
                                .findByOrganizationIdAndContributionTypeAndContributionId(organizationId,
                                        r.contributionType(), r.contributionId())
                                .orElseThrow(() -> ex);
                    }
                });
    }

    private RecognizedContribution toRecognizedContribution(RecognizedContributionEntity rc, CommunitySummary community,
            ContributionCatalogs catalogs, String viewerUserId) {

        var loves = loveRepository.findByOrganizationIdAndRecognizedContributionId(rc.getOrganizationId(), rc.getId());
        var received = loves.stream().filter(l -> !l.getLoverUserId().equals(viewerUserId)).count();
        var builder = RecognizedContribution.builder().contributionType(rc.getContributionType())
                .contributionId(rc.getContributionId()).communityId(rc.getCommunityId())
                .communitySlug(community == null ? null : community.slug())
                .communityName(community == null ? null : community.name()).title(rc.getTitle())
                .occurredAt(rc.getOccurredAt()).loveCount(received);
        enrich(builder, rc, catalogs.slotSources(), catalogs.artifactBadges());
        return builder.build();
    }

    private static void enrich(RecognizedContribution.RecognizedContributionBuilder builder,
            RecognizedContributionEntity rc, Map<Long, SlotSource> slotSources,
            Map<Long, ArtifactBadge> artifactBadges) {

        if (rc.getContributionType() == ContributionType.SLOT_PRESENTED) {
            var source = slotSources.get(rc.getContributionId());
            if (source != null) {
                builder.sessionId(source.sessionId()).typeId(source.sectionTypeId())
                        .typeLabel(source.sectionTypeLabel()).typeIcon(source.sectionTypeIcon())
                        .typeAccentColor(source.sectionTypeAccentColor());
            }
        } else {
            var badge = artifactBadges.get(rc.getContributionId());
            if (badge != null) {
                builder.typeId(badge.typeId()).typeLabel(badge.label()).typeIcon(badge.icon())
                        .typeAccentColor(badge.accentColor());
            }
        }
    }

    private Love toLove(LoveEntity l, RecognizedContributionEntity rc) {

        return Love.builder().id(l.getId()).contributionType(rc.getContributionType())
                .contributionId(rc.getContributionId()).communityId(rc.getCommunityId()).loverUserId(l.getLoverUserId())
                .createdAt(l.getCreatedAt()).build();
    }
}
