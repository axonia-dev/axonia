package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.OpenArea;
import dev.axonia.expertise.api.OpenDoors;
import dev.axonia.expertise.api.OpenPerson;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.PersonDirectory;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.AreaFold;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.Evidence;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.Person;
import dev.axonia.expertise.domain.Tier;
import dev.axonia.expertise.domain.TierPolicy;
import dev.axonia.expertise.domain.UserSignals;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpertiseService implements OpenDoors {

    private static final Evidence NO_EVIDENCE = Evidence.builder().presentedCount(0).contributedCount(0).offeredCount(0)
            .recent(List.of()).build();

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final ExpertiseQueryRepository expertiseQueryRepository;
    private final TagAliasRepository tagAliasRepository;
    private final EvidenceEnricher evidenceEnricher;
    private final DeclarationRepository declarationRepository;

    /**
     * Who to ask about the term, strongest claim first. {@code demonstratedOnly} narrows to the people who have done
     * the thing in this community, the practitioners and the contributors; by default everyone is listed, down to the
     * members whose only claim is a declaration.
     */
    @Transactional(readOnly = true)
    public List<Practitioner> whoKnows(String slug, String term, boolean demonstratedOnly) {

        if (term == null || term.isBlank()) {
            return List.of();
        }

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);
        var aliasMap = tagAliasRepository.aliasMap(org, community);
        var tags = expandTerm(term, aliasMap);

        var userSignals = expertiseQueryRepository.findPractitioners(org, community, tags);
        var declared = earliestDeclarationByUser(declarationRepository.visibleIn(org, community, tags));
        return buildPractitioners(userSignals, declared, demonstratedOnly).stream().sorted(searchOrder()).toList();
    }

    /**
     * The community's areas, each sized by the number of distinct people a search for it would list: two aggregate
     * reads, one for signals and one for declarations, folded onto the community's canonical names.
     */
    @Transactional(readOnly = true)
    public List<ExpertiseArea> areas(String slug, String search) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);
        var aliasMap = tagAliasRepository.aliasMap(org, community);

        var fold = new AreaFold(aliasMap);
        fold.carrySignals(expertiseQueryRepository.userTagAggregates(org, community));
        fold.carryDeclarations(declarationRepository.allVisibleIn(org, community));
        return fold.folded(search);
    }

    /**
     * The areas people are open to questions on, read under the same visibility rule as {@link #areas} and folded onto
     * the same canonical names, so a card here is the one the areas list and the search show.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OpenArea> openIn(String slug) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);
        var aliasMap = tagAliasRepository.aliasMap(org, community);

        var declarersByArea = new LinkedHashMap<String, Map<String, Instant>>();
        for (var declaration : declarationRepository.allVisibleIn(org, community)) {
            declarersByArea
                    .computeIfAbsent(AliasResolver.canonicalOf(declaration.tag(), aliasMap), k -> new HashMap<>())
                    .merge(declaration.userId(), declaration.declaredAt(),
                            BinaryOperator.minBy(Comparator.naturalOrder()));
        }
        var people = personDirectory
                .found(declarersByArea.values().stream().flatMap(declarers -> declarers.keySet().stream()).toList());
        return declarersByArea.entrySet().stream().map(area -> openArea(area.getKey(), area.getValue(), people))
                .filter(area -> !area.people().isEmpty())
                .sorted(Comparator.comparing(OpenArea::latestDeclaredAt, Comparator.reverseOrder())
                        .thenComparing(OpenArea::canonicalTag))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Practitioner> suggestSpeakers(String slug, Collection<String> rawTags) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);

        if (!communityAccess.isOrganizer(community, currentUser.userId())) {
            throw new ForbiddenException("Organizer role required");
        }

        var aliasMap = tagAliasRepository.aliasMap(org, community);
        var tags = expandRawTags(rawTags, aliasMap);

        var userSignals = expertiseQueryRepository.findPractitioners(org, community, tags);
        var practitioners = buildPractitioners(userSignals, Map.of(), true);
        return practitioners.stream().sorted(speakerOrder()).limit(8).toList();
    }

    private Set<String> expandTerm(String term, Map<String, String> aliasMap) {

        var normTerm = TagNormalizer.normalize(term);
        var canonical = AliasResolver.canonicalOf(normTerm, aliasMap);
        return AliasResolver.variantsOf(canonical, aliasMap);
    }

    private Set<String> expandRawTags(Collection<String> rawTags, Map<String, String> aliasMap) {

        var result = new HashSet<String>();
        for (var raw : rawTags) {
            result.addAll(expandTerm(raw, aliasMap));
        }
        return result;
    }

    /** The date a person first declared any of the searched tags, keyed by person, in the query's order. */
    private static Map<String, Instant> earliestDeclarationByUser(List<Declaration> declarations) {

        var byUser = new LinkedHashMap<String, Instant>();
        for (var declaration : declarations) {
            byUser.merge(declaration.userId(), declaration.declaredAt(),
                    BinaryOperator.minBy(Comparator.naturalOrder()));
        }
        return byUser;
    }

    private List<Practitioner> buildPractitioners(List<UserSignals> userSignals, Map<String, Instant> declared,
            boolean demonstratedOnly) {

        var evidenced = userSignals.stream().map(UserSignals::userId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var everyone = new LinkedHashSet<>(evidenced);
        everyone.addAll(declared.keySet());
        var people = personDirectory.resolve(everyone);

        var result = new ArrayList<Practitioner>();
        for (var us : userSignals) {
            var tier = TierPolicy.tierOf(us.signals());
            if (demonstratedOnly && tier == Tier.INTERESTED) {
                continue;
            }
            var evidence = evidenceEnricher.enrich(TierPolicy.evidenceOf(us.signals(), 5), us.signals());
            result.add(practitioner(new Candidate(us.userId(), tier, evidence, declared.get(us.userId())), people));
        }
        if (!demonstratedOnly) {
            declared.forEach((userId, declaredAt) -> {
                if (!evidenced.contains(userId)) {
                    result.add(practitioner(new Candidate(userId, Tier.OPEN_TO_QUESTIONS, NO_EVIDENCE, declaredAt),
                            people));
                }
            });
        }
        return result;
    }

    private static OpenArea openArea(String canonicalTag, Map<String, Instant> declarers, Map<String, Person> people) {

        var open = declarers.entrySet().stream().filter(declarer -> people.containsKey(declarer.getKey()))
                .map(declarer -> new Declarer(people.get(declarer.getKey()), declarer.getValue())).map(Declarer::open)
                .sorted(Comparator.comparing(OpenPerson::declaredAt, Comparator.reverseOrder())
                        .thenComparing(OpenPerson::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var latest = open.stream().map(OpenPerson::declaredAt).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        return new OpenArea(canonicalTag, latest, open);
    }

    private record Declarer(Person person, Instant declaredAt) {

        OpenPerson open() {

            return new OpenPerson(person.userId(), person.displayName(), person.avatarUrl(), declaredAt);
        }
    }

    private record Candidate(String userId, Tier tier, Evidence evidence, Instant declaredAt) {
    }

    private static Practitioner practitioner(Candidate candidate, Map<String, Person> people) {

        var builder = Practitioner.builder().userId(candidate.userId()).tier(candidate.tier())
                .evidence(candidate.evidence()).openToQuestions(candidate.declaredAt() != null)
                .declaredAt(candidate.declaredAt());
        var person = people.getOrDefault(candidate.userId(), Person.unresolved(candidate.userId()));
        return builder.displayName(person.displayName()).avatarUrl(person.avatarUrl()).orgUnit(person.orgUnit())
                .build();
    }

    /** Strongest claim first and, inside a tier, the people open to questions on the area before the rest. */
    private static Comparator<Practitioner> searchOrder() {

        return tierRank().thenComparing(Practitioner::openToQuestions, Comparator.reverseOrder())
                .thenComparing(recency());
    }

    /** A suggestion puts a person in front of a room, which willingness alone does not earn, so openness is no key. */
    private static Comparator<Practitioner> speakerOrder() {

        return tierRank().thenComparing(recency());
    }

    private static Comparator<Practitioner> tierRank() {

        return Comparator.comparingInt((Practitioner p) -> TierPolicy.rank(p.tier()));
    }

    private static Comparator<Practitioner> recency() {

        return Comparator.comparing(ExpertiseService::claimedAt, Comparator.reverseOrder())
                .thenComparing(Practitioner::displayName, String.CASE_INSENSITIVE_ORDER);
    }

    /** What dates a person's claim: their newest evidence, or, for a declaration, the day they declared. */
    private static Instant claimedAt(Practitioner p) {

        if (p.tier() == Tier.OPEN_TO_QUESTIONS) {
            return p.declaredAt();
        }
        return mostRecentOccurredAt(p);
    }

    private static Instant mostRecentOccurredAt(Practitioner p) {

        return p.evidence().recent().stream().map(EvidenceItem::occurredAt).max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }
}
