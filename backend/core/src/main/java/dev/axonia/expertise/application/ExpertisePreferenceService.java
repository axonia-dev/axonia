package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.PreferenceRepository;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.AreaCommunity;
import dev.axonia.expertise.domain.CommunitySummary;
import dev.axonia.expertise.domain.DeclarableCommunity;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.ExpertiseSignal;
import dev.axonia.expertise.domain.SelfDeclaration;
import dev.axonia.expertise.domain.SignalRef;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.expertise.domain.TierPolicy;
import lombok.RequiredArgsConstructor;

/**
 * Self-view and visibility-control operations for the current user's expertise profile.
 *
 * <p>
 * All operations are automatically scoped to {@code currentUser.userId()} +
 * {@code organizationContext.organizationId()}.
 *
 * <p>
 * The organization-wide self view is the one read that spans communities, and the tag vocabulary is owned by the
 * community, so every signal is canonicalized through the alias map of the community it was signalled in. The
 * community-scoped reads build the same view from one community's rows through the same code.
 */
@Service
@RequiredArgsConstructor
public class ExpertisePreferenceService {

    private static final int MAX_EVIDENCE_PAGE = 50;

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final ExpertiseQueryRepository expertiseQueryRepository;
    private final TagAliasRepository tagAliasRepository;
    private final PreferenceRepository preferenceRepository;
    private final EvidenceEnricher evidenceEnricher;
    private final CommunityAccess communityAccess;
    private final DeclarationRepository declarationRepository;

    /**
     * Returns the current user's full self-view: every area with tier, evidence, and hidden flag, plus the opt-out
     * status. Signals are returned regardless of opt-out / hidden-tag preferences so users can see exactly what has
     * been recorded. Each area's {@code communityTiers}, though, reads only what survives the visibility controls, so
     * it shows what that community actually sees. It also lists the communities the user can declare an area in.
     */
    @Transactional(readOnly = true)
    public SelfView selfView() {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();

        return selfViewOf(org, userId,
                new Recorded(findUserSignals(org, userId), expertiseQueryRepository.findVisibleForUser(org, userId),
                        declarationRepository.forUser(org, userId),
                        communityAccess.declarableCommunities(org, userId)));
    }

    /**
     * {@link #selfView()} inside one community: tiers, evidence, communities and declarations come from that
     * community's signals alone, and it is the only community offered to declare in. The opt-out and the hidden areas
     * stay the organization-wide preferences they are.
     */
    @Transactional(readOnly = true)
    public SelfView selfViewIn(String slug) {

        var org = organizationContext.organizationId();
        var userId = currentUser.userId();
        var community = communityAccess.communityIdForSlug(slug);
        var declarable = communityAccess.declarableCommunities(org, userId).stream()
                .filter(candidate -> candidate.id() == community).toList();

        return selfViewOf(org, userId,
                new Recorded(expertiseQueryRepository.findForUserIn(org, community, userId),
                        expertiseQueryRepository.findVisibleForUserIn(org, community, userId),
                        declarationRepository.inCommunity(org, community, userId), declarable));
    }

    /** Persists or clears the current user's opt-out flag. */
    public void optOut(boolean optedOut) {

        preferenceRepository.setOptOut(organizationContext.organizationId(), currentUser.userId(), optedOut);
    }

    /** Hides a tag from the current user's public expertise profile. The input is normalised. */
    public void hideTag(String canonicalTag) {

        var org = organizationContext.organizationId();
        preferenceRepository.hideTag(org, currentUser.userId(), normalize(canonicalTag));
    }

    /** Unhides a previously hidden tag. The input is normalised. */
    public void unhideTag(String canonicalTag) {

        var org = organizationContext.organizationId();
        preferenceRepository.unhideTag(org, currentUser.userId(), normalize(canonicalTag));
    }

    /** Dismisses a specific signal. The tag input is normalised. */
    public void dismissSignal(SourceType sourceType, Long sourceId, String canonicalTag) {

        var org = organizationContext.organizationId();
        preferenceRepository.dismissSignal(org, currentUser.userId(), new SignalRef(sourceType, sourceId),
                normalize(canonicalTag));
    }

    /** Returns one newest-first page of the given area's full evidence for the current user. */
    @Transactional(readOnly = true)
    public AreaEvidencePage areaEvidence(String tag, int offset, int limit) {

        var org = organizationContext.organizationId();
        return evidencePage(areaSignals(org, findUserSignals(org, currentUser.userId()), tag), offset, limit);
    }

    /** {@link #areaEvidence} inside one community: only the evidence recorded there. */
    @Transactional(readOnly = true)
    public AreaEvidencePage areaEvidenceIn(String slug, String tag, int offset, int limit) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);
        var signals = expertiseQueryRepository.findForUserIn(org, community, currentUser.userId());
        return evidencePage(areaSignals(org, signals, tag), offset, limit);
    }

    // ------------------------------------------------------------------
    // private helpers
    // ------------------------------------------------------------------

    private SelfView selfViewOf(String org, String userId, Recorded recorded) {

        var communityIds = Stream
                .concat(recorded.signals().stream().map(ExpertiseSignal::communityId),
                        recorded.declarations().stream().map(Declaration::communityId))
                .filter(Objects::nonNull).distinct().toList();
        var context = new SelfContext(tagAliasRepository.aliasMaps(org, communityIds),
                communityAccess.summaryById(communityIds), preferenceRepository.hiddenTags(org, userId));
        var optedOut = preferenceRepository.isOptedOut(org, userId);

        return new SelfView(optedOut, buildSelfAreas(recorded.signals(), recorded.visibleSignals(), context),
                buildSelfDeclarations(recorded.declarations(), context), recorded.declarable());
    }

    private List<ExpertiseSignal> areaSignals(String org, List<ExpertiseSignal> signals, String tag) {

        return groupByCanonical(signals, aliasMapsFor(org, signals)).getOrDefault(normalize(tag), List.of());
    }

    private AreaEvidencePage evidencePage(List<ExpertiseSignal> group, int offset, int limit) {

        var ordered = TierPolicy.orderedItems(group);
        var safeLimit = Math.min(Math.max(limit, 0), MAX_EVIDENCE_PAGE);
        var start = Math.min(Math.max(offset, 0), ordered.size());
        var end = Math.min(start + safeLimit, ordered.size());
        var enriched = evidenceEnricher.enrichItems(ordered.subList(start, end), group);
        return new AreaEvidencePage(enriched, ordered.size() > end);
    }

    private List<ExpertiseSignal> findUserSignals(String org, String userId) {

        var userSignalsList = expertiseQueryRepository.findForUser(org, userId);
        if (userSignalsList.isEmpty()) {
            return List.of();
        }
        return userSignalsList.get(0).signals();
    }

    private Map<Long, Map<String, String>> aliasMapsFor(String org, List<ExpertiseSignal> signals) {

        var communityIds = signals.stream().map(ExpertiseSignal::communityId).filter(Objects::nonNull).distinct()
                .toList();
        return tagAliasRepository.aliasMaps(org, communityIds);
    }

    /** What one self view is built from: every recorded signal, the ones that survive visibility, and declarations. */
    private record Recorded(List<ExpertiseSignal> signals, List<ExpertiseSignal> visibleSignals,
            List<Declaration> declarations, List<DeclarableCommunity> declarable) {
    }

    private record SelfContext(Map<Long, Map<String, String>> aliasMaps, Map<Long, CommunitySummary> summaries,
            Set<String> hiddenTags) {
    }

    private record DeclaredArea(long communityId, String canonicalTag) {
    }

    private static List<SelfDeclaration> buildSelfDeclarations(List<Declaration> declarations, SelfContext context) {

        var earliestByArea = declarations.stream().filter(d -> context.summaries().containsKey(d.communityId()))
                .collect(Collectors.toMap(
                        d -> new DeclaredArea(d.communityId(),
                                AliasResolver.canonicalOf(d.tag(),
                                        context.aliasMaps().getOrDefault(d.communityId(), Map.of()))),
                        Declaration::declaredAt, BinaryOperator.minBy(Comparator.naturalOrder())));
        return earliestByArea.entrySet().stream()
                .map(entry -> selfDeclaration(entry.getKey(), entry.getValue(), context))
                .sorted(Comparator.comparing(SelfDeclaration::communityName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SelfDeclaration::tag))
                .toList();
    }

    private static SelfDeclaration selfDeclaration(DeclaredArea area, Instant declaredAt, SelfContext context) {

        var community = context.summaries().get(area.communityId());
        return SelfDeclaration.builder().tag(area.canonicalTag()).communityId(area.communityId())
                .communityName(community.name()).communitySlug(community.slug())
                .hidden(context.hiddenTags().contains(area.canonicalTag())).declaredAt(declaredAt).build();
    }

    private List<SelfArea> buildSelfAreas(List<ExpertiseSignal> signals, List<ExpertiseSignal> visibleSignals,
            SelfContext context) {

        var byCanonical = groupByCanonical(signals, context.aliasMaps());
        var visibleByCanonical = groupByCanonical(visibleSignals, context.aliasMaps());
        var summaries = context.summaries();
        var hiddenTags = context.hiddenTags();

        var areas = new ArrayList<SelfArea>(byCanonical.size());
        for (var entry : byCanonical.entrySet()) {
            var canonical = entry.getKey();
            var group = entry.getValue();
            var tier = TierPolicy.tierOf(group);
            var evidence = evidenceEnricher.enrich(TierPolicy.evidenceOf(group, 5), group);
            var isHidden = hiddenTags.contains(canonical);
            var communityTiers = TierPolicy.tiersByCommunity(visibleByCanonical.getOrDefault(canonical, List.of()))
                    .stream().filter(t -> summaries.containsKey(t.communityId())).toList();
            areas.add(
                    new SelfArea(canonical, tier, isHidden, communitiesOf(group, summaries), evidence, communityTiers));
        }

        areas.sort(Comparator.comparingInt((SelfArea a) -> TierPolicy.rank(a.tier()))
                .thenComparing(SelfArea::canonicalTag));
        return List.copyOf(areas);
    }

    private static List<AreaCommunity> communitiesOf(List<ExpertiseSignal> group,
            Map<Long, CommunitySummary> summaries) {

        return group.stream().map(ExpertiseSignal::communityId).filter(Objects::nonNull).distinct()
                .filter(summaries::containsKey)
                .map(id -> new AreaCommunity(id, summaries.get(id).slug(), summaries.get(id).name()))
                .sorted(Comparator.comparing(AreaCommunity::name)).toList();
    }

    private LinkedHashMap<String, List<ExpertiseSignal>> groupByCanonical(List<ExpertiseSignal> signals,
            Map<Long, Map<String, String>> aliasMaps) {

        var map = new LinkedHashMap<String, List<ExpertiseSignal>>();
        for (var signal : signals) {
            var aliasMap = aliasMaps.getOrDefault(signal.communityId(), Map.of());
            var canonical = AliasResolver.canonicalOf(signal.tag(), aliasMap);
            map.computeIfAbsent(canonical, k -> new ArrayList<>()).add(signal);
        }
        return map;
    }

    /**
     * Hiding and dismissing are org-scoped personal preferences, stored as sent. A hidden tag is matched on the area a
     * row's community reads it as, a dismissal on that area or the stored tag (see PRACTITIONERS_SQL). So the tag is
     * normalised but not resolved here: with a community-owned vocabulary there is no single map to resolve it against.
     */
    private String normalize(String input) {

        return TagNormalizer.normalize(input);
    }
}
