package dev.axonia.agent.adapter.in.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import dev.axonia.agent.domain.AgentScope;
import dev.axonia.community.api.TagNormalizer;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.expertise.api.TagCuration;
import dev.axonia.expertise.api.TagMergeSuggestion;
import dev.axonia.expertise.api.TagUsageReport;
import dev.axonia.expertise.api.VocabularyTag;
import lombok.RequiredArgsConstructor;

/**
 * The tag curation tools, which keep a community's tags small and consistent: reading the vocabulary and the merges it
 * suggests, merging, splitting and renaming tags, parking a suggestion, and seeing what a tag is holding up before
 * deleting it. All of them are a curator's, under the same grant the curation page checks.
 *
 * <p>
 * Merging is one tool for one variant or many, since a single merge is just a batch of one and two tools would only
 * make an agent choose between them. Every write answers with the state it left, so the agent acts on what is there
 * rather than on what it assumed.
 */
@Component
@RequiredArgsConstructor
class CurationTools {

    private final AgentToolSupport support;
    private final TagCuration tagCuration;
    private final CommunityVocabulary communityVocabulary;

    record TagUsageView(String tag, int artifacts, int topics, int talks, int declarations, int learningIntents) {

        static TagUsageView of(String tag, TagUsageReport usage) {
            return new TagUsageView(tag, usage.artifacts(), usage.topics(), usage.talks(), usage.declarations(),
                    usage.learningIntents());
        }
    }

    record DeletedTagView(String tag) {
    }

    record NamedAreaRefView(long id, String name) {
    }

    record VocabularyTagView(String tag, List<String> variants, int people, NamedAreaRefView namedArea) {

        static VocabularyTagView of(VocabularyTag tag) {
            if (tag == null) {
                return null;
            }
            var area = tag.namedArea() == null
                    ? null
                    : new NamedAreaRefView(tag.namedArea().id(), tag.namedArea().name());
            return new VocabularyTagView(tag.tag(), tag.variants(), tag.peopleCount(), area);
        }
    }

    record SuggestedTagView(String tag, List<String> variants, int people, TagUsageView usage) {

        static SuggestedTagView of(TagMergeSuggestion.Side side) {
            return new SuggestedTagView(side.tag(), side.variants(), side.peopleCount(),
                    TagUsageView.of(side.tag(), side.usage()));
        }
    }

    record MergeSuggestionView(SuggestedTagView tagA, SuggestedTagView tagB, double similarity) {
    }

    record SplitView(VocabularyTagView variant, VocabularyTagView formerCanonical) {
    }

    record SuggestionPairView(String tagA, String tagB, boolean dismissed) {
    }

    @McpTool(name = "tag.list", description = "List the tags a community already uses, by their canonical names, "
            + "sorted. Call it before tagging anything and reuse an existing tag rather than inventing a "
            + "near-duplicate. Open to any member.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<String> list(@McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("tag.list", AgentScope.READ, () -> communityVocabulary.visibleTags(slug));
    }

    @McpTool(name = "tag.vocabulary", description = "List a community's tags for curating them: each canonical tag "
            + "with the spellings merged into it that content still carries (its variants), how many people can be "
            + "asked about it, and the named area it belongs to. Curators only.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<VocabularyTagView> vocabulary(
            @McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("tag.vocabulary", AgentScope.READ,
                () -> tagCuration.vocabulary(slug).stream().map(VocabularyTagView::of).toList());
    }

    @McpTool(name = "tag.suggestions", description = "List the " + TagCuration.SUGGESTION_LIMIT + " most alike pairs "
            + "of a community's tags, the likeliest duplicates, each with its similarity from 0 to 1, the spellings already merged into each tag, how "
            + "many people can be asked about it and what content carries it. Judge each pair: merge a real duplicate "
            + "with tag.merge, usually the rarer spelling into the more used one, and park two distinct things with "
            + "tag.dismissSuggestion so the pair is not suggested again; the next pairs then surface. Curators only.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<MergeSuggestionView> suggestions(
            @McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("tag.suggestions", AgentScope.READ, () -> tagCuration.suggestions(slug).stream().map(
                s -> new MergeSuggestionView(SuggestedTagView.of(s.tagA()), SuggestedTagView.of(s.tagB()), s.score()))
                .toList());
    }

    @McpTool(name = "tag.merge", description = "Merge one or more variant tags into a canonical tag in a community, "
            + "all of them or none. Content keeps the spelling it was tagged with and is read as the canonical "
            + "everywhere, so tag.split undoes a merge. A variant already merged elsewhere moves to this canonical. "
            + "The canonical cannot itself be a variant, a variant cannot have tags merged into it, and every tag must "
            + "already be in the community's vocabulary. Answers the canonical with the variants content still "
            + "carries. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public VocabularyTagView merge(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "The tags to merge away, at least one", required = true) List<String> variants,
            @McpToolParam(description = "The tag they are merged into", required = true) String canonical) {
        return support.run("tag.merge", AgentScope.TAG_CURATE,
                () -> VocabularyTagView.of(tagCuration.merge(slug, variants, canonical)));
    }

    @McpTool(name = "tag.split", description = "Undo a merge: the variant stands on its own again, with the content "
            + "that carries it. Splitting a tag that is not merged changes nothing. Answers both tags as they are now; "
            + "formerCanonical is null when the tag was not merged. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public SplitView split(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "The merged variant", required = true) String variant) {
        return support.run("tag.split", AgentScope.TAG_CURATE, () -> {
            var split = tagCuration.split(slug, variant);
            return new SplitView(VocabularyTagView.of(split.variant()), VocabularyTagView.of(split.formerCanonical()));
        });
    }

    @McpTool(name = "tag.rename", description = "Rename a tag on all of a community's content, its named area and the "
            + "expertise built on it. Renaming onto a name another tag already carries folds the two into one, which "
            + "cannot be undone: prefer tag.merge for duplicates. A tag that is merged, or has tags merged into it, "
            + "must be split first. Answers the tag under its new name. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public VocabularyTagView rename(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Current tag", required = true) String from,
            @McpToolParam(description = "New name", required = true) String to) {
        return support.run("tag.rename", AgentScope.TAG_CURATE,
                () -> VocabularyTagView.of(tagCuration.rename(slug, from, to)));
    }

    @McpTool(name = "tag.dismissSuggestion", description = "Park a suggested merge as two distinct tags, so "
            + "tag.suggestions stops listing the pair. Undo with tag.undismissSuggestion. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public SuggestionPairView dismissSuggestion(
            @McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "One tag of the pair", required = true) String tagA,
            @McpToolParam(description = "The other tag of the pair", required = true) String tagB) {
        return support.run("tag.dismissSuggestion", AgentScope.TAG_CURATE, () -> {
            tagCuration.dismiss(slug, tagA, tagB);
            return new SuggestionPairView(TagNormalizer.normalize(tagA), TagNormalizer.normalize(tagB), true);
        });
    }

    @McpTool(name = "tag.undismissSuggestion", description = "Let tag.suggestions list a pair you parked with "
            + "tag.dismissSuggestion again. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public SuggestionPairView undismissSuggestion(
            @McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "One tag of the pair", required = true) String tagA,
            @McpToolParam(description = "The other tag of the pair", required = true) String tagB) {
        return support.run("tag.undismissSuggestion", AgentScope.TAG_CURATE, () -> {
            tagCuration.undismiss(slug, tagA, tagB);
            return new SuggestionPairView(TagNormalizer.normalize(tagA), TagNormalizer.normalize(tagB), false);
        });
    }

    @McpTool(name = "tag.usage", description = "Count what carries a tag in a community: artifacts, topics, talks, "
            + "members who declared it as expertise, and members who want to learn it. Call it before tag.delete to "
            + "know what the delete will remove. Curators only.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public TagUsageView tagUsage(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Tag", required = true) String tag) {
        return support.run("tag.usage", AgentScope.READ, () -> TagUsageView.of(tag, tagCuration.usage(slug, tag)));
    }

    @McpTool(name = "tag.delete", description = "Delete a tag from a community: it is stripped from every artifact, "
            + "topic, talk, learning intent and expertise declaration carrying it, and stops counting toward anyone's "
            + "expertise. To fold a duplicate into another tag, use tag.merge instead: a merge keeps the content and "
            + "its history, and tag.split undoes it. A tag merged into another must be split first. Curators only, "
            + "and it cannot be undone.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public DeletedTagView tagDelete(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Tag", required = true) String tag) {
        return support.run("tag.delete", AgentScope.TAG_DELETE, () -> {
            tagCuration.delete(slug, tag);
            return new DeletedTagView(tag);
        });
    }
}
