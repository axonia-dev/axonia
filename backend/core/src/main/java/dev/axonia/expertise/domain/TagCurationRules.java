package dev.axonia.expertise.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.axonia.community.api.TagNormalizer;
import dev.axonia.shared.api.error.ValidationException;

/**
 * The rules a curator's verbs answer to, stated once and free of any I/O: what a community's vocabulary is, which tags
 * a curator may touch from it, and what makes a name writable onto content.
 */
public final class TagCurationRules {

    /** The source columns a rename writes into are {@code varchar(50)}, not the expertise projection's 80. */
    public static final int MAX_TAG_LENGTH = 50;

    private TagCurationRules() {
    }

    /**
     * Every name this community produced, whether by a signal or by a declaration: the raw tags, their canonicals, and
     * the variants of those canonicals.
     */
    public static Set<String> vocabularyOf(Collection<String> rawTags, Map<String, String> aliasMap) {

        var vocabulary = new HashSet<>(rawTags);
        for (var tag : rawTags) {
            var canonical = AliasResolver.canonicalOf(tag, aliasMap);
            vocabulary.add(canonical);
            vocabulary.addAll(AliasResolver.variantsOf(canonical, aliasMap));
        }
        return vocabulary;
    }

    /**
     * The aliases the community can actually see: those whose variant is still carried here, by a signal or by a
     * declaration. An alias row for a variant nothing carries draws no chip on the curation card, so it cannot be a
     * verdict either. Otherwise a curator is told to split a merge that no chip in front of them can split, and the tag
     * is stranded: neither renameable nor deleteable, ever. Both sides of the rule read the same source, the raw tags.
     */
    public static Map<String, String> liveAliases(Collection<String> rawTags, Map<String, String> aliasMap) {

        var carried = new HashSet<>(rawTags);
        return aliasMap.entrySet().stream().filter(entry -> carried.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The schema, not this rule, is what bounds a curator to their community. What remains here is narrower: a curator
     * may not write vocabulary rows about tags their community has never produced.
     */
    public static void requireInVocabulary(Set<String> vocabulary, Collection<String> normalizedTags) {

        var outside = normalizedTags.stream().filter(tag -> !vocabulary.contains(tag)).sorted().toList();
        if (!outside.isEmpty()) {
            throw new ValidationException("TAG_NOT_IN_COMMUNITY",
                    "Tags outside this community's vocabulary cannot be curated from it: "
                            + String.join(", ", outside));
        }
    }

    /**
     * Rename and delete rewrite content, and a merged tag has no single right answer for what should happen to its
     * variants. Splitting first is one reversible click, so the rule is a guard rather than a limitation.
     */
    public static void requireStandalone(String tag, Map<String, String> aliases) {

        if (aliases.containsKey(tag)) {
            throw new ValidationException("TAG_MERGED",
                    "'" + tag + "' is merged into '" + aliases.get(tag) + "'. Split it out first.");
        }
        if (aliases.containsValue(tag)) {
            throw new ValidationException("TAG_MERGED",
                    "'" + tag + "' has other tags merged into it. Split them out first.");
        }
    }

    /** Validates a whole merge batch before anything is written. */
    public static void requireMergeableVariants(Collection<String> normVariants, String normCanonical,
            Map<String, String> aliases) {

        for (var normVariant : normVariants) {
            if (normVariant.equals(normCanonical)) {
                throw new ValidationException("SELF_MERGE", "A tag cannot be merged into itself: " + normVariant);
            }
            if (aliases.containsValue(normVariant)) {
                throw new ValidationException("ALIAS_CYCLE",
                        "Cycle detected: '" + normVariant + "' is already used as a canonical tag");
            }
        }
    }

    /** The normalized name a rename may write onto content, or a rejection of it. */
    public static String requireWritableName(String to) {

        var normalized = TagNormalizer.normalize(to);
        if (normalized.isBlank()) {
            throw new ValidationException("TAG_BLANK", "A tag needs a name");
        }
        if (normalized.length() > MAX_TAG_LENGTH) {
            throw new ValidationException("TAG_TOO_LONG",
                    "A tag can be at most " + MAX_TAG_LENGTH + " characters: " + normalized);
        }
        return normalized;
    }

    /** The pair in the order the store keeps it: {@code tagLow} sorts before or equal to {@code tagHigh}. */
    public static TagPair sortedPair(String tagA, String tagB) {

        var normA = TagNormalizer.normalize(tagA);
        var normB = TagNormalizer.normalize(tagB);
        return normA.compareTo(normB) <= 0 ? new TagPair(normA, normB) : new TagPair(normB, normA);
    }

    /** A merge folds one name into another; a rename may not target a name that has been folded away. */
    public static void requireNotAVariant(String normTo, Map<String, String> aliases) {

        if (aliases.containsKey(normTo)) {
            throw new ValidationException("RENAME_TARGET_MERGED", "'" + normTo + "' has been merged into '"
                    + aliases.get(normTo) + "' and is not a name content can carry");
        }
    }

    /** Renaming a tag to the name it already carries asks for nothing. */
    public static void requireDifferentName(String normFrom, String normTo) {

        if (normFrom.equals(normTo)) {
            throw new ValidationException("SELF_RENAME", "That is already the tag's name: " + normTo);
        }
    }
}
