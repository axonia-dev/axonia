package dev.axonia.recognition.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.api.ArtifactBadge;
import dev.axonia.recognition.api.ContributionLoved;
import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.ContributionUnloved;
import dev.axonia.recognition.api.SlotSource;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.LoveStore;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.application.port.out.RecognitionStore;
import dev.axonia.recognition.domain.CommunitySummary;
import dev.axonia.recognition.domain.GivenLove;
import dev.axonia.recognition.domain.Love;
import dev.axonia.recognition.domain.Recognition;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecognitionService {

    private final RecognitionStore recognitionStore;
    private final LoveStore loveStore;
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
        var saved = loveStore
                .add(GivenLove.builder().organizationId(organizationId).recognizedContributionId(rc.id())
                        .loverUserId(userId).build())
                .orElseThrow(() -> new ConflictException("ALREADY_LOVED", "You have already loved this contribution"));
        events.publishEvent(new ContributionLoved(rc.contributionType(), rc.contributionId(), rc.communityId()));
        return toLove(saved, rc);
    }

    @Transactional
    public void unlove(ContributionType contributionType, Long contributionId) {

        var organizationId = organizationContext.organizationId();
        var rc = recognitionStore.find(organizationId, contributionType, contributionId)
                .orElseThrow(() -> new NotFoundException("No love to remove"));
        var love = loveStore.find(organizationId, rc.id(), currentUser.userId())
                .orElseThrow(() -> new NotFoundException("No love to remove"));
        loveStore.remove(love);
        events.publishEvent(new ContributionUnloved(rc.contributionType(), rc.contributionId(), rc.communityId()));
    }

    @Transactional(readOnly = true)
    public LoveSummary summary(ContributionType contributionType, Long contributionId) {

        var organizationId = organizationContext.organizationId();
        var rc = recognitionStore.find(organizationId, contributionType, contributionId);
        if (rc.isEmpty()) {
            return LoveSummary.builder().count(0).viewerHasLoved(false).build();
        }
        var loves = loveStore.ofRecognition(organizationId, rc.get().id());
        var userId = currentUser.userId();
        return LoveSummary.builder().count(loves.size())
                .viewerHasLoved(loves.stream().anyMatch(l -> l.loverUserId().equals(userId))).build();
    }

    @Transactional(readOnly = true)
    public List<RecognizedContribution> myContributions() {

        var organizationId = organizationContext.organizationId();
        var userId = currentUser.userId();
        var entities = new ArrayList<Recognition>();
        entities.addAll(recognizedOf(organizationId, ContributionType.SLOT_PRESENTED,
                slotSourceAccess.slotsPresentedBy(organizationId, userId)));
        entities.addAll(recognizedOf(organizationId, ContributionType.ARTIFACT_CAPTURED,
                artifactSourceAccess.artifactsCapturedBy(organizationId, userId)));
        entities.sort(Comparator.comparing(Recognition::occurredAt).reversed());
        var summaries = communityAccess.summaryById(entities.stream().map(Recognition::communityId).toList());
        var slotSources = slotSourceAccess.slotSources(idsOfType(entities, ContributionType.SLOT_PRESENTED));
        var artifactBadges = artifactTypeAccess.artifactTypes(idsOfType(entities, ContributionType.ARTIFACT_CAPTURED));
        var catalogs = new ContributionCatalogs(slotSources, artifactBadges);
        return entities.stream()
                .map(rc -> toRecognizedContribution(rc, summaries.get(rc.communityId()), catalogs, userId)).toList();
    }

    /** Groups the lookup tables {@link #toRecognizedContribution} needs, to stay under the parameter-count limit. */
    private record ContributionCatalogs(Map<Long, SlotSource> slotSources, Map<Long, ArtifactBadge> artifactBadges) {
    }

    private List<Recognition> recognizedOf(String organizationId, ContributionType type, List<Long> contributionIds) {

        if (contributionIds.isEmpty()) {
            return List.of();
        }
        return recognitionStore.findAll(organizationId, type, contributionIds);
    }

    private static List<Long> idsOfType(List<Recognition> entities, ContributionType type) {

        return entities.stream().filter(e -> e.contributionType() == type).map(Recognition::contributionId).toList();
    }

    private ContributionSource resolveSource(String organizationId, LoveRequest r) {

        var source = switch (r.contributionType()) {
            case ARTIFACT_CAPTURED -> artifactSourceAccess.artifactSource(organizationId, r.contributionId());
            case SLOT_PRESENTED -> slotSourceAccess.slotContribution(organizationId, r.contributionId());
        };
        return source.orElseThrow(() -> new NotFoundException("Contribution not found: " + r.contributionId()));
    }

    private Recognition findOrCreate(String organizationId, LoveRequest r, ContributionSource source) {

        return recognitionStore.recognize(Recognition.builder().organizationId(organizationId)
                .contributionType(r.contributionType()).contributionId(r.contributionId())
                .communityId(source.communityId()).title(source.title()).occurredAt(source.occurredAt()).build());
    }

    private RecognizedContribution toRecognizedContribution(Recognition rc, CommunitySummary community,
            ContributionCatalogs catalogs, String viewerUserId) {

        var loves = loveStore.ofRecognition(rc.organizationId(), rc.id());
        var received = loves.stream().filter(l -> !l.loverUserId().equals(viewerUserId)).count();
        var builder = RecognizedContribution.builder().contributionType(rc.contributionType())
                .contributionId(rc.contributionId()).communityId(rc.communityId())
                .communitySlug(community == null ? null : community.slug())
                .communityName(community == null ? null : community.name()).title(rc.title())
                .occurredAt(rc.occurredAt()).loveCount(received);
        enrich(builder, rc, catalogs.slotSources(), catalogs.artifactBadges());
        return builder.build();
    }

    private static void enrich(RecognizedContribution.RecognizedContributionBuilder builder, Recognition rc,
            Map<Long, SlotSource> slotSources, Map<Long, ArtifactBadge> artifactBadges) {

        if (rc.contributionType() == ContributionType.SLOT_PRESENTED) {
            var source = slotSources.get(rc.contributionId());
            if (source != null) {
                builder.sessionId(source.sessionId()).typeId(source.sectionTypeId())
                        .typeLabel(source.sectionTypeLabel()).typeIcon(source.sectionTypeIcon())
                        .typeAccentColor(source.sectionTypeAccentColor());
            }
        } else {
            var badge = artifactBadges.get(rc.contributionId());
            if (badge != null) {
                builder.typeId(badge.typeId()).typeLabel(badge.label()).typeIcon(badge.icon())
                        .typeAccentColor(badge.accentColor());
            }
        }
    }

    private Love toLove(GivenLove l, Recognition rc) {

        return Love.builder().id(l.id()).contributionType(rc.contributionType()).contributionId(rc.contributionId())
                .communityId(rc.communityId()).loverUserId(l.loverUserId()).createdAt(l.createdAt()).build();
    }
}
