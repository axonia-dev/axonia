package dev.axonia.expertise.application;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.NamedAreaRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.PersonDirectory;
import dev.axonia.expertise.application.port.out.SourceReachResolver;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseSignal;
import dev.axonia.expertise.domain.Person;
import dev.axonia.expertise.domain.SourceRef;
import dev.axonia.expertise.domain.TopicMap;
import dev.axonia.expertise.domain.TopicMapAssembler;
import dev.axonia.expertise.domain.UserTagAggregate;
import lombok.RequiredArgsConstructor;

/**
 * The two reads the map view makes: the areas and their links, and the contributions that fill them. They live together
 * because they answer one request and share the alias fold and the person lookup.
 */
@Service
@RequiredArgsConstructor
public class ExpertiseMapService {

    /**
     * The most contributions one map payload carries. No number the page states comes from this payload, so the cap
     * thins the picture and never changes a fact.
     */
    public static final int MAP_CONTRIBUTION_LIMIT = 5000;

    private final OrganizationContext organizationContext;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final ExpertiseQueryRepository expertiseQueryRepository;
    private final TagAliasRepository tagAliasRepository;
    private final DeclarationRepository declarationRepository;
    private final EvidenceEnricher evidenceEnricher;
    private final SourceReachResolver sourceReachResolver;
    private final NamedAreaRepository namedAreaRepository;

    /** The community's areas, the links between them and its named areas. Read-only; writes nothing. */
    @Transactional(readOnly = true)
    public TopicMap topicMap(String slug) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);
        var aliasMap = tagAliasRepository.aliasMap(org, community);
        var rows = expertiseQueryRepository.userTagAggregates(org, community);
        var declarations = declarationRepository.allVisibleIn(org, community);

        // One directory call for every person on the map, not one per area. Declarers are deliberately absent:
        // this resolve exists to name a top holder, and a declaration never makes anyone one.
        var userIds = rows.stream().map(UserTagAggregate::userId).collect(Collectors.toSet());
        var people = personDirectory.resolve(userIds);

        return TopicMapAssembler.assemble(TopicMapAssembler.Sources.builder().rows(rows).declarations(declarations)
                .namedAreas(namedAreaRepository.findAll(org, community)).aliasMap(aliasMap)
                .nameOf(userId -> people.getOrDefault(userId, Person.unresolved(userId)).displayName()).build());
    }

    /** Every contribution the map draws, capped, with its authors and each presented slot's reach. */
    @Transactional(readOnly = true)
    public MapContributions contributions(String slug) {

        var org = organizationContext.organizationId();
        var community = communityAccess.communityIdForSlug(slug);

        // One more than the cap, so a full page can be told from an exactly-full one without a second count query.
        var fetched = expertiseQueryRepository.visibleSignals(org, community, MAP_CONTRIBUTION_LIMIT + 1);
        var truncated = fetched.size() > MAP_CONTRIBUTION_LIMIT;
        var signals = truncated ? fetched.subList(0, MAP_CONTRIBUTION_LIMIT) : fetched;

        var aliasMap = tagAliasRepository.aliasMap(org, community);
        var enriched = evidenceEnricher.enrichItems(itemsOf(signals), signals);
        var reach = sourceReachResolver.reachFor(refsOf(signals));

        var items = new ArrayList<MapContribution>(signals.size());
        for (var i = 0; i < signals.size(); i++) {
            var signal = signals.get(i);
            items.add(
                    new MapContribution(signal.id(), AliasResolver.canonicalOf(signal.tag(), aliasMap), signal.userId(),
                            reach.get(new SourceRef(signal.sourceType(), signal.sourceId())), enriched.get(i)));
        }

        return new MapContributions(peopleOf(signals), List.copyOf(items), truncated);
    }

    private static List<EvidenceItem> itemsOf(List<ExpertiseSignal> signals) {

        return signals.stream().map(s -> EvidenceItem.builder().signalType(s.signalType()).sourceType(s.sourceType())
                .sourceId(s.sourceId()).occurredAt(s.occurredAt()).build()).toList();
    }

    private static List<SourceRef> refsOf(List<ExpertiseSignal> signals) {

        return signals.stream().map(s -> new SourceRef(s.sourceType(), s.sourceId())).distinct().toList();
    }

    private List<MapPerson> peopleOf(List<ExpertiseSignal> signals) {

        var userIds = signals.stream().map(ExpertiseSignal::userId).distinct().toList();
        var resolved = personDirectory.resolve(userIds);
        return userIds.stream().map(userId -> {
            var person = resolved.getOrDefault(userId, Person.unresolved(userId));
            return new MapPerson(userId, person.displayName(), person.avatarUrl());
        }).toList();
    }
}
