package dev.axonia.expertise;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.NotFoundException;
import dev.axonia.TagNormalizer;
import dev.axonia.ValidationException;
import dev.axonia.expertise.internal.AliasResolver;
import dev.axonia.expertise.internal.CurationScope;
import dev.axonia.expertise.internal.NamedAreaRepository;
import dev.axonia.expertise.internal.TagAliasRepository;
import dev.axonia.expertise.internal.TagCurationRules;
import dev.axonia.expertise.internal.VocabularyLock;
import lombok.RequiredArgsConstructor;

/**
 * A curator groups the community's canonical tags into named areas. A tag is always stored under the canonical the
 * community's merges read it as, so a named area never holds a spelling the map folds away.
 */
@Service
@RequiredArgsConstructor
public class NamedAreaCurationService {

    /** The width of the stored name column. */
    private static final int MAX_NAME_LENGTH = 80;

    private final OrganizationContext organizationContext;
    private final CurationScope curationScope;
    private final TagAliasRepository tagAliasRepository;
    private final NamedAreaRepository namedAreaRepository;
    private final VocabularyLock vocabularyLock;

    @Transactional(readOnly = true)
    public List<NamedArea> namedAreas(String slug) {

        var communityId = curationScope.requireCurationAccess(slug);
        return namedAreaRepository.findAll(organizationContext.organizationId(), communityId);
    }

    @Transactional
    public NamedArea createNamedArea(String slug, String name) {

        var communityId = curationScope.requireCurationAccess(slug);
        var trimmed = requireName(name);
        var id = namedAreaRepository.create(organizationContext.organizationId(), communityId, trimmed);
        return new NamedArea(id, trimmed, List.of());
    }

    @Transactional
    public void renameNamedArea(String slug, long id, String name) {

        var communityId = curationScope.requireCurationAccess(slug);
        var trimmed = requireName(name);
        requireFound(namedAreaRepository.rename(organizationContext.organizationId(), communityId, id, trimmed), id);
    }

    @Transactional
    public void deleteNamedArea(String slug, long id) {

        var communityId = curationScope.requireCurationAccess(slug);
        requireFound(namedAreaRepository.delete(organizationContext.organizationId(), communityId, id), id);
    }

    /**
     * Moves the tag out of any other named area it sat in. The community lock orders it after a concurrent tag rename,
     * merge or delete, so the canonical it reads is still the one the tag has when the row is written.
     */
    @Transactional
    public void placeInNamedArea(String slug, long id, String tag) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var vocabulary = curationScope.vocabularyOf(org, communityId);
        var canonical = AliasResolver.canonicalOf(TagNormalizer.normalize(tag), vocabulary.aliases());
        TagCurationRules.requireInVocabulary(vocabulary.names(), List.of(canonical));
        requireFound(namedAreaRepository.place(org, communityId, id, canonical), id);
    }

    /** Open on a tag the community no longer carries, so a membership the map drops can still be taken back. */
    @Transactional
    public void removeFromNamedArea(String slug, String tag) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var canonical = AliasResolver.canonicalOf(TagNormalizer.normalize(tag),
                tagAliasRepository.aliasMap(org, communityId));
        namedAreaRepository.remove(org, communityId, canonical);
    }

    private static String requireName(String name) {

        var trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("NAMED_AREA_NAME_BLANK", "A named area needs a name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("NAMED_AREA_NAME_TOO_LONG",
                    "A named area's name can be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static void requireFound(boolean found, long id) {

        if (!found) {
            throw new NotFoundException("No named area " + id + " in this community");
        }
    }
}
