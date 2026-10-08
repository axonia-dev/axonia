package dev.axonia.expertise;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.CommunityTagWriter;
import dev.axonia.TagNormalizer;
import dev.axonia.TagUsageKind;
import dev.axonia.ValidationException;
import dev.axonia.expertise.internal.AliasResolver;
import dev.axonia.expertise.internal.CurationScope;
import dev.axonia.expertise.internal.DeclarationRepository;
import dev.axonia.expertise.internal.ExpertiseQueryRepository;
import dev.axonia.expertise.internal.NamedAreaRepository;
import dev.axonia.expertise.internal.SignalWriter;
import dev.axonia.expertise.internal.TagAliasRepository;
import dev.axonia.expertise.internal.TagCurationRules;
import dev.axonia.expertise.internal.TagDismissalRepository;
import dev.axonia.expertise.internal.TagSimilarity;
import dev.axonia.expertise.internal.VocabularyLock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpertiseCurationService {

    private static final double MERGE_THRESHOLD = 0.7;

    private final OrganizationContext organizationContext;
    private final CurationScope curationScope;
    private final ExpertiseQueryRepository expertiseQueryRepository;
    private final TagAliasRepository tagAliasRepository;
    private final TagDismissalRepository tagDismissalRepository;
    private final List<CommunityTagWriter> tagWriters;
    private final SignalWriter signalWriter;
    private final DeclarationRepository declarationRepository;
    private final NamedAreaRepository namedAreaRepository;
    private final VocabularyLock vocabularyLock;

    /**
     * The areas a curator works on: every name the community produced, signalled or declared, and every name another
     * context's records carry (topics, talks, artifacts, learning intents), unfiltered by anyone's privacy choice,
     * folded onto its canonical. The chips are the spellings still carried, which is what
     * {@link TagCurationRules#liveAliases} keeps a merge alive for, so the card always draws the chip the rules ask a
     * curator to split. The count is the search's, so an area only hidden people carry shows 0 rather than vanishing.
     */
    @Transactional(readOnly = true)
    public List<ExpertiseArea> vocabulary(String slug) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();

        var fold = new AreaFold(tagAliasRepository.aliasMap(org, communityId));
        fold.carryNames(curationScope.rawTagsOf(org, communityId));
        fold.carrySignals(expertiseQueryRepository.userTagAggregates(org, communityId));
        fold.carryDeclarations(declarationRepository.allVisibleIn(org, communityId));
        return fold.folded(null);
    }

    @Transactional(readOnly = true)
    public List<MergeSuggestion> suggestions(String slug) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        var aliasMap = tagAliasRepository.aliasMap(org, communityId);
        // The candidates are the raw names the community produced, signalled or declared, plus every name another
        // context's records carry (topics, talks, artifacts, learning intents): the misspelling worth catching is
        // often the one a member typed into a declare or learn field. Merged variants collapse to their
        // canonical so an already-merged name never resurfaces as a candidate, as the areas listing does.
        var canonicalTags = curationScope.rawTagsOf(org, communityId).stream()
                .map(tag -> AliasResolver.canonicalOf(tag, aliasMap)).distinct().toList();
        var dismissed = tagDismissalRepository.dismissedPairs(org, communityId);
        return TagSimilarity.suggestMerges(canonicalTags, MERGE_THRESHOLD).stream()
                .map(p -> new MergeSuggestion(p.tagA(), p.tagB(), p.score()))
                .filter(s -> !dismissed.contains(TagCurationRules.sortedPair(s.tagA(), s.tagB()))).toList();
    }

    @Transactional
    public void merge(String slug, String variant, String canonical) {

        mergeMany(slug, List.of(variant), canonical);
    }

    @Transactional
    public void mergeMany(String slug, List<String> variants, String canonical) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var normCanonical = TagNormalizer.normalize(canonical);
        var aliases = tagAliasRepository.aliasMap(org, communityId);

        // Reject cycle: canonical is itself a variant pointing elsewhere.
        if (aliases.containsKey(normCanonical)) {
            throw new ValidationException("ALIAS_CYCLE", "Cycle detected: '" + normCanonical
                    + "' is already a variant pointing to '" + aliases.get(normCanonical) + "'");
        }

        var normVariants = variants.stream().map(TagNormalizer::normalize).distinct().toList();
        requireTagsInCommunityVocabulary(org, communityId,
                Stream.concat(normVariants.stream(), Stream.of(normCanonical)).toList());

        TagCurationRules.requireMergeableVariants(normVariants, normCanonical, aliases);

        for (var normVariant : normVariants) {
            var previous = aliases.get(normVariant);
            if (normCanonical.equals(previous)) {
                continue;
            }
            // Moving a variant to another area unmerges it from the one it read as first.
            if (previous != null) {
                unmerge(org, communityId, normVariant, previous);
            }
            signalWriter.carryHiddenArea(org, communityId, normVariant, normCanonical);
            tagAliasRepository.addAlias(org, communityId, normVariant, normCanonical);
            // A named area holds canonicals only, so the variant's placement becomes the canonical's unless the
            // canonical already sits somewhere.
            namedAreaRepository.renameTag(org, communityId, normVariant, normCanonical);
        }
    }

    /**
     * Unmerges the variant. A member who hid the area, or dismissed a signal under it, stays hidden under the variant.
     */
    @Transactional
    public void split(String slug, String variant) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var normVariant = TagNormalizer.normalize(variant);
        requireTagsInCommunityVocabulary(org, communityId, List.of(normVariant));
        var canonical = tagAliasRepository.aliasMap(org, communityId).get(normVariant);
        if (canonical != null) {
            unmerge(org, communityId, normVariant, canonical);
        }
    }

    public void dismiss(String slug, String tagA, String tagB) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        var normA = TagNormalizer.normalize(tagA);
        var normB = TagNormalizer.normalize(tagB);
        requireTagsInCommunityVocabulary(org, communityId, List.of(normA, normB));
        if (normA.equals(normB)) {
            throw new ValidationException("SELF_DISMISS", "A tag cannot be dismissed against itself: " + normA);
        }
        var pair = TagCurationRules.sortedPair(tagA, tagB);
        tagDismissalRepository.addDismissal(org, communityId, pair.tagLow(), pair.tagHigh());
    }

    public void undismiss(String slug, String tagA, String tagB) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        var normA = TagNormalizer.normalize(tagA);
        var normB = TagNormalizer.normalize(tagB);
        requireTagsInCommunityVocabulary(org, communityId, List.of(normA, normB));
        var pair = TagCurationRules.sortedPair(tagA, tagB);
        tagDismissalRepository.removeDismissal(org, communityId, pair.tagLow(), pair.tagHigh());
    }

    /**
     * What the tag is holding up right now, across every context that owns tagged content and across the members who
     * declared it. The members are counted once each, over the spellings a delete would remove, so the dialog names
     * what it is about to take away.
     */
    @Transactional(readOnly = true)
    public TagUsageReport usage(String slug, String tag) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        var normalized = TagNormalizer.normalize(tag);
        var vocabulary = curationScope.vocabularyOf(org, communityId);
        TagCurationRules.requireInVocabulary(vocabulary.names(), List.of(normalized));

        var counts = new EnumMap<TagUsageKind, Integer>(TagUsageKind.class);
        for (var writer : tagWriters) {
            for (var usage : writer.usage(communityId, normalized)) {
                counts.merge(usage.kind(), usage.count(), Integer::sum);
            }
        }
        var declarations = declarationRepository.countIn(org, communityId,
                AliasResolver.variantsOf(normalized, vocabulary.aliases()));
        return new TagUsageReport(counts.getOrDefault(TagUsageKind.ARTIFACT, 0),
                counts.getOrDefault(TagUsageKind.TOPIC, 0), counts.getOrDefault(TagUsageKind.TALK, 0), declarations,
                counts.getOrDefault(TagUsageKind.LEARNING_INTENT, 0));
    }

    /**
     * Renames the tag on the community's real content, not just on the expertise overlay. Every context owning tagged
     * content is rewritten, then the projection follows, all in one transaction. The old name stops existing, so the
     * pairs parked about it stop being verdicts, exactly as they do on a delete.
     */
    @Transactional
    public void rename(String slug, String from, String to) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var vocabulary = curationScope.vocabularyOf(org, communityId);

        var normFrom = TagNormalizer.normalize(from);
        TagCurationRules.requireInVocabulary(vocabulary.names(), List.of(normFrom));
        TagCurationRules.requireStandalone(normFrom, vocabulary.liveAliases());
        var normTo = TagCurationRules.requireWritableName(to);
        TagCurationRules.requireDifferentName(normFrom, normTo);
        TagCurationRules.requireNotAVariant(normTo, vocabulary.liveAliases());

        signalWriter.carryHiddenArea(org, communityId, normFrom, normTo);
        // Declarations are stored as typed and read through the aliases. A retired name that was a variant keeps
        // meaning its canonical.
        var aliases = vocabulary.aliases();
        for (var retired : List.of(normFrom, normTo)) {
            if (aliases.containsKey(retired)) {
                moveSpelling(org, communityId, retired, aliases.get(retired));
            }
        }
        for (var writer : tagWriters) {
            writer.rename(communityId, normFrom, normTo);
        }
        var renamedSpellings = spellingsOtherThan(normTo, AliasResolver.variantsOf(normFrom, aliases));
        for (var spelling : renamedSpellings) {
            signalWriter.renameTag(org, communityId, spelling, normTo);
            declarationRepository.renameTag(org, communityId, spelling, normTo);
        }
        namedAreaRepository.renameTag(org, communityId, normFrom, normTo);
        retireVocabularyRowsNaming(org, communityId, normFrom);
        // The new name arrives free of history: an alias row that outlived its signals must not fold it away. The
        // merges into the new name stay, so its variants' signals and declarations keep reading as it.
        tagAliasRepository.removeAlias(org, communityId, normTo);
    }

    /**
     * Strips the tag from the community's content, from the projection, from the pairs that named it, and from the
     * members' declarations and the named areas of it under any spelling that reads as it. A named area it empties
     * stays: a person named it, and only a person ends it.
     */
    @Transactional
    public void delete(String slug, String tag) {

        var communityId = curationScope.requireCurationAccess(slug);
        var org = organizationContext.organizationId();
        vocabularyLock.lock(org, communityId);
        var vocabulary = curationScope.vocabularyOf(org, communityId);

        var normalized = TagNormalizer.normalize(tag);
        TagCurationRules.requireInVocabulary(vocabulary.names(), List.of(normalized));
        TagCurationRules.requireStandalone(normalized, vocabulary.liveAliases());

        signalWriter.dropHiddenArea(org, communityId, normalized);
        for (var writer : tagWriters) {
            writer.remove(communityId, normalized);
        }
        var aliases = vocabulary.aliases();
        // A variant's declarations mean its canonical, which survives the delete.
        if (aliases.containsKey(normalized)) {
            moveSpelling(org, communityId, normalized, aliases.get(normalized));
        }
        var removedSpellings = AliasResolver.variantsOf(normalized, aliases).stream().sorted().toList();
        for (var spelling : removedSpellings) {
            signalWriter.removeTag(org, communityId, spelling);
            declarationRepository.removeTag(org, communityId, spelling);
            namedAreaRepository.removeNaming(org, communityId, spelling);
        }
        retireVocabularyRowsNaming(org, communityId, normalized);
    }

    private void unmerge(String org, long communityId, String variant, String canonical) {

        signalWriter.carryOverSplit(org, communityId, variant, canonical);
        tagAliasRepository.removeAlias(org, communityId, variant);
    }

    /** Moves a dead spelling's declarations onto the name it reads as. It carries no signals. */
    private void moveSpelling(String org, long communityId, String spelling, String name) {

        declarationRepository.renameTag(org, communityId, spelling, name);
    }

    private static List<String> spellingsOtherThan(String name, Set<String> spellings) {

        return spellings.stream().filter(spelling -> !spelling.equals(name)).sorted().toList();
    }

    private void retireVocabularyRowsNaming(String org, long communityId, String tag) {

        tagAliasRepository.removeAliasesNaming(org, communityId, tag);
        tagDismissalRepository.removeDismissalsNaming(org, communityId, tag);
    }

    private void requireTagsInCommunityVocabulary(String org, long communityId, Collection<String> normalizedTags) {

        TagCurationRules.requireInVocabulary(curationScope.vocabularyOf(org, communityId).names(), normalizedTags);
    }
}
