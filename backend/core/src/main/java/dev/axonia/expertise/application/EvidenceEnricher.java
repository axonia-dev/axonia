package dev.axonia.expertise.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.SourceLabelResolver;
import dev.axonia.expertise.domain.Evidence;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseSignal;
import dev.axonia.expertise.domain.SourceRef;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.recognition.api.ArtifactBadge;
import dev.axonia.recognition.api.SlotSource;
import dev.axonia.recognition.api.spi.ArtifactTypeAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import lombok.RequiredArgsConstructor;

/**
 * Fills each evidence item's {@code label} (source title), {@code communitySlug}, and — for slots and artifacts — the
 * deep-link {@code sessionId} and the real type badge (id/label/icon/accent) in one pass. Titles come from the
 * {@link SourceLabelResolver}; slugs from {@link CommunityAccess}; slot sessions/section types and artifact types from
 * their owning modules via {@link SlotSourceAccess} / {@link ArtifactTypeAccess}. The {@code signals} argument supplies
 * the source-to-community mapping and is expected to be the same signal set the evidence was derived from. Unresolved
 * titles become {@code null} (no title) and unresolved slugs {@code null} (not linkable); counts are never touched.
 */
@Component
@RequiredArgsConstructor
public class EvidenceEnricher {

    private final SourceLabelResolver sourceLabelResolver;
    private final CommunityAccess communityAccess;
    private final SlotSourceAccess slotSourceAccess;
    private final ArtifactTypeAccess artifactTypeAccess;

    /**
     * Returns exactly one enriched item per input item, in input order, so a caller holding a parallel list joins the
     * two by index. Any filtering added here breaks that join silently.
     */
    public List<EvidenceItem> enrichItems(List<EvidenceItem> items, List<ExpertiseSignal> signals) {

        if (items.isEmpty()) {
            return items;
        }

        var refToCommunity = new LinkedHashMap<SourceRef, Long>();
        for (var signal : signals) {
            refToCommunity.putIfAbsent(new SourceRef(signal.sourceType(), signal.sourceId()), signal.communityId());
        }

        var refs = items.stream().map(item -> new SourceRef(item.sourceType(), item.sourceId())).distinct().toList();
        var titles = sourceLabelResolver.titlesFor(refs);
        var summaries = communityAccess
                .summaryById(refs.stream().map(refToCommunity::get).filter(Objects::nonNull).distinct().toList());
        var slotSources = slotSourceAccess.slotSources(idsOf(refs, SourceType.SLOT));
        var artifactBadges = artifactTypeAccess.artifactTypes(idsOf(refs, SourceType.ARTIFACT));

        return items.stream().map(item -> {
            var ref = new SourceRef(item.sourceType(), item.sourceId());
            var communityId = refToCommunity.get(ref);
            var summary = communityId == null ? null : summaries.get(communityId);
            var slug = summary == null ? null : summary.slug();
            var builder = EvidenceItem.builder().signalType(item.signalType()).sourceType(item.sourceType())
                    .sourceId(item.sourceId()).label(titles.get(ref)).communitySlug(slug).occurredAt(item.occurredAt());
            applyBadge(builder, item, slotSources, artifactBadges);
            return builder.build();
        }).toList();
    }

    public Evidence enrich(Evidence evidence, List<ExpertiseSignal> signals) {

        var enriched = enrichItems(evidence.recent(), signals);
        return Evidence.builder().presentedCount(evidence.presentedCount())
                .contributedCount(evidence.contributedCount()).offeredCount(evidence.offeredCount()).recent(enriched)
                .build();
    }

    private static List<Long> idsOf(List<SourceRef> refs, SourceType type) {

        return refs.stream().filter(ref -> ref.sourceType() == type).map(SourceRef::sourceId).toList();
    }

    private static void applyBadge(EvidenceItem.EvidenceItemBuilder builder, EvidenceItem item,
            Map<Long, SlotSource> slotSources, Map<Long, ArtifactBadge> artifactBadges) {

        if (item.sourceType() == SourceType.SLOT) {
            var source = slotSources.get(item.sourceId());
            if (source != null) {
                builder.sessionId(source.sessionId()).typeId(source.sectionTypeId())
                        .typeLabel(source.sectionTypeLabel()).typeIcon(source.sectionTypeIcon())
                        .typeAccentColor(source.sectionTypeAccentColor());
            }
        } else if (item.sourceType() == SourceType.ARTIFACT) {
            var badge = artifactBadges.get(item.sourceId());
            if (badge != null) {
                builder.typeId(badge.typeId()).typeLabel(badge.label()).typeIcon(badge.icon())
                        .typeAccentColor(badge.accentColor());
            }
        }
    }
}
