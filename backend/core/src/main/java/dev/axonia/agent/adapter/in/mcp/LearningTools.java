package dev.axonia.agent.adapter.in.mcp;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import dev.axonia.agent.domain.AgentScope;
import dev.axonia.learning.api.HeldArea;
import dev.axonia.learning.api.LearningIntents;
import lombok.RequiredArgsConstructor;

/**
 * The four learning tools: reading what a community wants to learn and who, and adding or removing the caller's own
 * areas. Removing is destructive, since it drops the date the area was named, and idempotent.
 */
@Component
@RequiredArgsConstructor
class LearningTools {

    private final AgentToolSupport support;
    private final LearningIntents learning;

    record DemandView(String tag, int learners, int canBeAsked, boolean held) {
    }

    /** No email, deliberately, as on the roster. */
    record LearnerView(String userId, String displayName, Instant since) {
    }

    record HeldView(String tag, Instant since) {
    }

    record RemovedView(String tag) {
    }

    @McpTool(name = "learning.demand", description = "List the areas a community's members want to learn, most "
            + "wanted first, with how many want each and how many can be asked about it. held marks the areas you "
            + "want to learn yourself.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<DemandView> demand(@McpToolParam(description = "Community slug", required = true) String slug) {
        return support.run("learning.demand", AgentScope.READ, () -> {
            var page = learning.page(slug);
            var held = page.mine().stream().map(HeldArea::tag).collect(Collectors.toSet());
            return page.wanted().stream().map(wanted -> new DemandView(wanted.tag(), wanted.learners(),
                    wanted.canBeAsked(), held.contains(wanted.tag()))).toList();
        });
    }

    @McpTool(name = "learning.learners", description = "List the members who want to learn an area, earliest first, "
            + "with the date each said so. Returns user ids and display names, never emails.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<LearnerView> learners(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Area, any spelling the community uses", required = true) String area) {
        return support.run("learning.learners", AgentScope.MEMBERS_READ,
                () -> learning.learners(slug, area).stream()
                        .map(learner -> new LearnerView(learner.userId(), learner.displayName(), learner.declaredAt()))
                        .toList());
    }

    @McpTool(name = "learning.add", description = "Say you want to learn an area in a community. Adding an area you "
            + "already hold, under any spelling, returns it unchanged. At most " + LearningIntents.INTENT_LIMIT
            + " per community.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true))
    public HeldView add(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Area, e.g. kubernetes", required = true) String area) {
        return support.run("learning.add", AgentScope.LEARNING_DECLARE, () -> {
            var held = learning.add(slug, area);
            return new HeldView(held.tag(), held.declaredAt());
        });
    }

    @McpTool(name = "learning.remove", description = "Stop wanting to learn an area in a community, under every "
            + "spelling you held it by. Returns the area as the community reads it. Removing an area you do not "
            + "hold changes nothing.", annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true))
    public RemovedView remove(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Area", required = true) String area) {
        return support.run("learning.remove", AgentScope.LEARNING_DECLARE,
                () -> new RemovedView(learning.remove(slug, area)));
    }
}
