package dev.axonia.expertise.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.spi.CommunityTagWriter;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.TagCurationRules;
import dev.axonia.shared.api.error.ForbiddenException;
import lombok.RequiredArgsConstructor;

/** What a curator works within: the community they may curate, and the names it knows. */
@Component
@RequiredArgsConstructor
public class CurationScope {

    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final ExpertiseQueryRepository expertiseQueryRepository;
    private final DeclarationRepository declarationRepository;
    private final TagAliasRepository tagAliasRepository;
    private final List<CommunityTagWriter> tagWriters;

    /** @return the id of the community the slug names, once the current user is shown to curate its tags */
    public long requireCurationAccess(String slug) {

        var communityId = communityAccess.communityIdForSlug(slug);
        if (!communityAccess.canCurateTags(communityId, currentUser.userId())) {
            throw new ForbiddenException("Tag curation is not open to you");
        }
        return communityId;
    }

    /**
     * What the community's curation page is looking at: every name its signals and its declarations know, and the
     * merges it can actually see, which are the ones a card draws a splittable chip for. A declared name is curated
     * like any other, so a tag nobody ever signalled is still merged, renamed and deleted from here.
     */
    @Transactional(readOnly = true)
    public CommunityVocabulary vocabularyOf(String org, long communityId) {

        var rawTags = rawTagsOf(org, communityId);
        var aliases = tagAliasRepository.aliasMap(org, communityId);
        return new CommunityVocabulary(TagCurationRules.vocabularyOf(rawTags, aliases),
                TagCurationRules.liveAliases(rawTags, aliases), aliases);
    }

    /**
     * Every name this community produced, under the spelling it was stored as: its signals, its declarations and every
     * tag another context's records carry.
     */
    @Transactional(readOnly = true)
    public List<String> rawTagsOf(String org, long communityId) {

        var fromWriters = tagWriters.stream().flatMap(writer -> writer.tagsIn(communityId).stream());
        return Stream
                .of(expertiseQueryRepository.allTags(org, communityId).stream(),
                        declarationRepository.tagsIn(org, communityId).stream(), fromWriters)
                .flatMap(s -> s).distinct().toList();
    }

    public record CommunityVocabulary(Set<String> names, Map<String, String> liveAliases, Map<String, String> aliases) {

        public CommunityVocabulary {
            names = Set.copyOf(names);
            liveAliases = Map.copyOf(liveAliases);
            aliases = Map.copyOf(aliases);
        }
    }
}
