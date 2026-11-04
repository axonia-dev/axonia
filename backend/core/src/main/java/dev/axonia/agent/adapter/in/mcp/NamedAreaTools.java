package dev.axonia.agent.adapter.in.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import dev.axonia.agent.domain.AgentScope;
import dev.axonia.expertise.api.CuratedNamedArea;
import dev.axonia.expertise.api.NamedAreaCuration;
import lombok.RequiredArgsConstructor;

/**
 * The named-area tools: grouping a community's tags into areas a curator names, the way the expertise map shows them.
 * All of them are a curator's, under the same grant the curation page checks, and each write answers with the area as
 * it now is.
 *
 * <p>
 * A tag belongs to at most one named area, so assigning one is a move whenever another area held it. The assignment
 * says so rather than leaving the agent to notice that another area shrank.
 */
@Component
@RequiredArgsConstructor
class NamedAreaTools {

    private final AgentToolSupport support;
    private final NamedAreaCuration namedAreaCuration;

    record NamedAreaView(long id, String name, List<String> tags) {

        static NamedAreaView of(CuratedNamedArea area) {
            return new NamedAreaView(area.id(), area.name(), area.tags());
        }
    }

    record MovedTagView(String tag, long fromAreaId, String fromAreaName) {
    }

    record AssignmentView(NamedAreaView area, List<MovedTagView> moved) {
    }

    record UnassignmentView(String tag, NamedAreaView fromArea) {
    }

    @McpTool(name = "namedArea.list", description = "List a community's named areas, each with its tags. A tag belongs "
            + "to at most one named area. Curators only.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<NamedAreaView> list(@McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("namedArea.list", AgentScope.READ,
                () -> namedAreaCuration.namedAreas(slug).stream().map(NamedAreaView::of).toList());
    }

    @McpTool(name = "namedArea.create", description = "Create an empty named area in a community, then fill it with "
            + "namedArea.assignTags. Names are up to 80 characters. Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false))
    public NamedAreaView create(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Area name", required = true) String name) {
        return support.run("namedArea.create", AgentScope.TAG_CURATE,
                () -> NamedAreaView.of(namedAreaCuration.create(slug, name)));
    }

    @McpTool(name = "namedArea.rename", description = "Rename a named area. Answers the area with its tags. Curators "
            + "only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public NamedAreaView rename(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Named area id, from namedArea.list", required = true) Long id,
            @McpToolParam(description = "New name", required = true) String name) {
        return support.run("namedArea.rename", AgentScope.TAG_CURATE,
                () -> NamedAreaView.of(namedAreaCuration.rename(slug, id, name)));
    }

    @McpTool(name = "namedArea.delete", description = "Delete a named area. Its tags are not touched: they stay on "
            + "their content and in the vocabulary, and simply belong to no named area anymore. Answers the area as it "
            + "was, with the tags it released, so they can be assigned elsewhere. Curators only, and it cannot be "
            + "undone.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false))
    public NamedAreaView delete(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Named area id, from namedArea.list", required = true) Long id) {
        return support.run("namedArea.delete", AgentScope.TAG_CURATE,
                () -> NamedAreaView.of(namedAreaCuration.delete(slug, id)));
    }

    @McpTool(name = "namedArea.assignTags", description = "Assign several tags to a named area in one call, all of "
            + "them or none. A merged variant is assigned as its canonical. A tag belongs to at most one named area, "
            + "so a tag another area held moves out of it: the answer lists each such move under moved. Every tag "
            + "must be in the community's vocabulary (see tag.vocabulary). Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public AssignmentView assignTags(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Named area id, from namedArea.list or namedArea.create", required = true) Long id,
            @McpToolParam(description = "The tags to assign, at least one", required = true) List<String> tags) {
        return support.run("namedArea.assignTags", AgentScope.TAG_CURATE, () -> {
            var assignment = namedAreaCuration.assign(slug, id, tags);
            return new AssignmentView(NamedAreaView.of(assignment.area()), assignment.moved().stream()
                    .map(move -> new MovedTagView(move.tag(), move.fromId(), move.fromName())).toList());
        });
    }

    @McpTool(name = "namedArea.unassignTag", description = "Take a tag out of whichever named area holds it; the tag "
            + "itself stays. A merged variant is unassigned as its canonical, so the answer names the canonical. Answers the area it left, without it, or null for fromArea when no area held it. "
            + "Curators only.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public UnassignmentView unassignTag(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Tag", required = true) String tag) {
        return support.run("namedArea.unassignTag", AgentScope.TAG_CURATE, () -> {
            var unassignment = namedAreaCuration.unassign(slug, tag);
            return new UnassignmentView(unassignment.tag(),
                    unassignment.fromArea().map(NamedAreaView::of).orElse(null));
        });
    }
}
