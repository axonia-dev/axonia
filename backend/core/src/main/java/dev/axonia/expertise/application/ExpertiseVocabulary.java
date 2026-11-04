package dev.axonia.expertise.application;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.expertise.api.TagReading;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.TagCurationRules;
import lombok.RequiredArgsConstructor;

/**
 * What another context needs to keep records named in a community's vocabulary: its lock, its areas, and who can be
 * asked about each. The reads take the caller's organization from the request, so they must not be called off a
 * request, for example from an event listener or a scheduled job. The lock takes the organization explicitly instead.
 */
@Service
@RequiredArgsConstructor
public class ExpertiseVocabulary implements CommunityVocabulary {

    private final OrganizationContext organizationContext;
    private final TagAliasRepository tagAliasRepository;
    private final VocabularyLock vocabularyLock;
    private final ExpertiseService expertiseService;
    private final CurationScope curationScope;

    /**
     * Takes the community's vocabulary lock shared until the caller's transaction ends, so no curator rewrites the
     * names a record is about to be stored under. The organization is the caller's to name: a member's departure can
     * arrive on a path that carries no member token.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void shareLock(String org, long communityId) {

        vocabularyLock.share(org, communityId);
    }

    /**
     * The community's areas, each with every spelling merged into it. Reads the caller's organization from the request,
     * so it must not be called off a request.
     */
    @Override
    @Transactional(readOnly = true)
    public CommunityAreas areasOf(long communityId) {

        return new CommunityAreas(tagAliasRepository.aliasMap(organizationContext.organizationId(), communityId));
    }

    /**
     * Per canonical area, the number of people a search for it would list: the count the areas list shows. Reads the
     * caller's organization from the request, so it must not be called off a request.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> canBeAsked(String slug) {

        return expertiseService.areas(slug, null).stream()
                .collect(Collectors.toMap(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount));
    }

    /** The normalized name a record may be stored under, or a rejection of it. */
    @Override
    public String writableName(String raw) {

        return TagCurationRules.requireWritableName(raw);
    }

    /**
     * Reads the caller's organization from the request, so it must not be called off a request. A merged tag is
     * reported as merged even when no content carries it yet, since the merge already folds it.
     */
    @Override
    @Transactional(readOnly = true)
    public TagReading read(long communityId, Collection<String> rawTags) {

        var vocabulary = curationScope.vocabularyOf(organizationContext.organizationId(), communityId);
        var aliases = vocabulary.aliases();
        var tags = rawTags.stream().map(TagNormalizer::normalize).filter(tag -> !tag.isBlank()).distinct().toList();
        var merged = tags.stream().filter(aliases::containsKey).map(tag -> new TagReading.Merged(tag, aliases.get(tag)))
                .toList();
        var created = tags.stream().filter(tag -> !aliases.containsKey(tag) && !vocabulary.names().contains(tag))
                .toList();
        return new TagReading(created, merged);
    }
}
