package dev.axonia.agent.adapter.in.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import dev.axonia.agent.domain.AgentScope;
import dev.axonia.expertise.api.TagCuration;
import lombok.RequiredArgsConstructor;

/**
 * The two tag curation tools: seeing what a tag is holding up across a community, and deleting it. Both are a
 * curator's, and the delete is meant to follow the usage, so the agent can say what it is about to remove.
 */
@Component
@RequiredArgsConstructor
class CurationTools {

    private final AgentToolSupport support;
    private final TagCuration tagCuration;

    record TagUsageView(String tag, int artifacts, int topics, int talks, int declarations, int learningIntents) {
    }

    record DeletedTagView(String tag) {
    }

    @McpTool(name = "tag.usage", description = "Count what carries a tag in a community: artifacts, topics, talks, "
            + "members who declared it as expertise, and members who want to learn it. Call it before tag.delete to "
            + "know what the delete will remove. Curators only.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public TagUsageView tagUsage(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Tag", required = true) String tag) {
        return support.run("tag.usage", AgentScope.READ, () -> {
            var usage = tagCuration.usage(slug, tag);
            return new TagUsageView(tag, usage.artifacts(), usage.topics(), usage.talks(), usage.declarations(),
                    usage.learningIntents());
        });
    }

    @McpTool(name = "tag.delete", description = "Delete a tag from a community: it is stripped from every artifact, "
            + "topic, talk, learning intent and expertise declaration carrying it, and stops counting toward anyone's "
            + "expertise. A tag merged into another must be split first. Curators only, and it cannot be "
            + "undone.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public DeletedTagView tagDelete(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Tag", required = true) String tag) {
        return support.run("tag.delete", AgentScope.TAG_DELETE, () -> {
            tagCuration.delete(slug, tag);
            return new DeletedTagView(tag);
        });
    }
}
