package dev.axonia.expertise.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.application.port.out.NamedAreaRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.NamedArea;
import dev.axonia.expertise.domain.NamedAreaPlacement;
import dev.axonia.expertise.domain.TagCurationRules;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
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

    /** @return the named area under its new name, with its tags */
    @Transactional
    public NamedArea renameNamedArea(String slug, long id, String name) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        var trimmed = requireName(name);
        requireFound(namedAreaRepository.rename(org, communityId, id, trimmed), id);
        return new NamedArea(id, trimmed, tagsOf(org, communityId, id));
    }

    /**
     * Its tags stay in the vocabulary and simply belong to no named area anymore.
     *
     * @return the named area as it was, with the tags it released
     */
    @Transactional
    public NamedArea deleteNamedArea(String slug, long id) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var before = namedAreaRepository.findAll(org, communityId).stream().filter(area -> area.id() == id).findFirst();
        requireFound(namedAreaRepository.delete(org, communityId, id), id);
        return before.orElseThrow();
    }

    /**
     * Moves the tag out of any other named area it sat in. The community lock orders it after a concurrent tag rename,
     * merge or delete, so the canonical it reads is still the one the tag has when the row is written.
     */
    @Transactional
    public void placeInNamedArea(String slug, long id, String tag) {

        placeAllInNamedArea(slug, id, List.of(tag));
    }

    /**
     * Places every tag, each under the canonical the community reads it as, or none of them: one tag outside the
     * vocabulary refuses the whole call. A tag another named area held moves, and the answer says from where.
     */
    @Transactional
    public NamedAreaPlacement placeAllInNamedArea(String slug, long id, List<String> tags) {

        var communityId = curationScope.requireCurationAccess(slug);
        if (tags.isEmpty()) {
            throw new ValidationException("NAMED_AREA_NO_TAGS", "Name at least one tag to place");
        }
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var vocabulary = curationScope.vocabularyOf(org, communityId);
        var canonicals = tags.stream()
                .map(tag -> AliasResolver.canonicalOf(TagNormalizer.normalize(tag), vocabulary.aliases())).distinct()
                .toList();
        TagCurationRules.requireInVocabulary(vocabulary.names(), canonicals);
        var before = namedAreaRepository.findAll(org, communityId);
        for (var canonical : canonicals) {
            requireFound(namedAreaRepository.place(org, communityId, id, canonical), id);
        }
        var area = namedAreaRepository.findAll(org, communityId).stream().filter(placed -> placed.id() == id)
                .findFirst().orElseThrow();
        return new NamedAreaPlacement(area, movesInto(id, canonicals, before));
    }

    /**
     * Open on a tag the community no longer carries, so a membership the map drops can still be taken back.
     *
     * @return the canonical the tag was taken out as, and the named area it left, without it, or none when no named
     *         area held it
     */
    @Transactional
    public TagRemoval removeFromNamedArea(String slug, String tag) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var canonical = AliasResolver.canonicalOf(TagNormalizer.normalize(tag),
                tagAliasRepository.aliasMap(org, communityId));
        var holder = holderOf(canonical, namedAreaRepository.findAll(org, communityId));
        namedAreaRepository.remove(org, communityId, canonical);
        return new TagRemoval(canonical,
                holder.map(area -> new NamedArea(area.id(), area.name(), tagsOf(org, communityId, area.id()))));
    }

    /** The canonical a tag was taken out of a named area as, and the area it left, if any held it. */
    public record TagRemoval(String tag, Optional<NamedArea> from) {
    }

    private List<String> tagsOf(String org, long communityId, long id) {

        return namedAreaRepository.findAll(org, communityId).stream().filter(area -> area.id() == id)
                .flatMap(area -> area.tags().stream()).toList();
    }

    private static List<NamedAreaPlacement.Move> movesInto(long id, List<String> canonicals, List<NamedArea> before) {

        return canonicals.stream().flatMap(tag -> holderOf(tag, before).filter(area -> area.id() != id).stream()
                .map(area -> new NamedAreaPlacement.Move(tag, area.id(), area.name()))).toList();
    }

    private static Optional<NamedArea> holderOf(String tag, List<NamedArea> areas) {

        return areas.stream().filter(area -> area.tags().contains(tag)).findFirst();
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
