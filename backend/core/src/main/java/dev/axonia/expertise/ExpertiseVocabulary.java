package dev.axonia.expertise;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.expertise.internal.TagAliasRepository;
import dev.axonia.expertise.internal.TagCurationRules;
import dev.axonia.expertise.internal.VocabularyLock;
import lombok.RequiredArgsConstructor;

/**
 * What another context needs to keep records named in a community's vocabulary: its lock, its areas, and who can be
 * asked about each. The reads take the caller's organization from the request, so they must not be called off a
 * request, for example from an event listener or a scheduled job. The lock takes the organization explicitly instead.
 */
@Service
@RequiredArgsConstructor
public class ExpertiseVocabulary {

    private final OrganizationContext organizationContext;
    private final TagAliasRepository tagAliasRepository;
    private final VocabularyLock vocabularyLock;
    private final ExpertiseService expertiseService;

    /**
     * Takes the community's vocabulary lock shared until the caller's transaction ends, so no curator rewrites the
     * names a record is about to be stored under. The organization is the caller's to name: a member's departure can
     * arrive on a path that carries no member token.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void shareLock(String org, long communityId) {

        vocabularyLock.share(org, communityId);
    }

    /**
     * The community's areas, each with every spelling merged into it. Reads the caller's organization from the request,
     * so it must not be called off a request.
     */
    @Transactional(readOnly = true)
    public CommunityAreas areasOf(long communityId) {

        return new CommunityAreas(tagAliasRepository.aliasMap(organizationContext.organizationId(), communityId));
    }

    /**
     * Per canonical area, the number of people a search for it would list: the count the areas list shows. Reads the
     * caller's organization from the request, so it must not be called off a request.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> canBeAsked(String slug) {

        return expertiseService.areas(slug, null).stream()
                .collect(Collectors.toMap(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount));
    }

    /** The normalized name a record may be stored under, or a rejection of it. */
    public String writableName(String raw) {

        return TagCurationRules.requireWritableName(raw);
    }
}
