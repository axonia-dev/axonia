package dev.axonia.agent.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.ForbiddenException;
import dev.axonia.agent.AgentScope;
import dev.axonia.community.CommunityService;
import dev.axonia.community.MemberDirectory;
import dev.axonia.community.MembershipService;
import dev.axonia.events.ScheduleService;
import dev.axonia.events.Section;
import dev.axonia.events.SectionTypeService;
import dev.axonia.events.Session;
import dev.axonia.events.SessionService;
import dev.axonia.events.Slot;
import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactFilter;
import dev.axonia.knowledge.ArtifactService;
import dev.axonia.knowledge.ArtifactTypeService;
import dev.axonia.knowledge.ResourceService;
import lombok.RequiredArgsConstructor;

/**
 * The eight read-only agent tools: browsing communities, artifact types, schedules, sessions, library content, session
 * artifacts, section types, and a community's roster.
 */
@Component
@RequiredArgsConstructor
class ReadTools {

    /** What the roster returns when the caller asks for no size, and the ceiling on what it will return at all. */
    private static final int DEFAULT_MEMBER_PAGE_SIZE = 25;
    private static final int MAX_MEMBER_PAGE_SIZE = 100;

    private final AgentToolSupport support;
    private final CommunityService communities;
    private final MembershipService memberships;
    private final ArtifactTypeService artifactTypes;
    private final ScheduleService schedule;
    private final SessionService sessions;
    private final ArtifactService artifacts;
    private final ResourceService resources;
    private final SectionTypeService sectionTypes;
    private final MemberDirectory memberDirectory;

    record CommunityView(String slug, String name, long memberCount, boolean member) {
    }

    /** No email, deliberately: an email is the field that turns a roster into a phishing list. */
    record CommunityMemberView(String userId, String displayName) {
    }

    record ArtifactTypeView(Long id, String label, long artifactCount) {
    }

    record ScheduleView(Long sessionId, String title, Instant scheduledAt, Integer durationMinutes, String status,
            boolean virtual) {
    }

    record SessionView(Long id, String title, Instant scheduledAt, Integer durationMinutes, String status,
            List<SectionView> sections) {
    }

    record SectionView(Long sectionId, String title, List<SlotView> slots) {
    }

    record SlotView(Long slotId, String title, boolean open) {
    }

    /** Grouped into one record (rather than eleven method params) to stay under the parameter-count limit. */
    record SearchParams(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Full-text query over title and description", required = false) String q,
            @McpToolParam(description = "Artifact type id, from library.artifactTypes", required = false) Long typeId,
            @McpToolParam(description = "A single tag to filter by", required = false) String tag,
            @McpToolParam(description = "Contributor user id", required = false) String contributor,
            @McpToolParam(description = "Created at or after, ISO-8601 instant", required = false) String from,
            @McpToolParam(description = "Created at or before, ISO-8601 instant", required = false) String to,
            @McpToolParam(description = "Only artifacts attached to this session id", required = false) Long sessionId,
            @McpToolParam(description = "newest (default) or mostAppreciated", required = false) String sort,
            @McpToolParam(description = "Zero-based page index, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 20, maximum 50", required = false) Integer size) {
    }

    record ArtifactView(Long id, String title, String url, String type, String description, List<String> tags,
            String contributorUserId, int loveCount, Instant createdAt) {
    }

    record SessionArtifactView(Long attachmentId, Long segmentId, String attachedByUserId, ArtifactView artifact) {
    }

    record SectionTypeView(Long id, String label, boolean expectsPresenter) {
    }

    @McpTool(name = "communities.list", description = "List the communities in your organization, with whether you "
            + "are a member of each. Use a community's slug for the other tools.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<CommunityView> listCommunities() {
        return support.run("communities.list", AgentScope.READ, () -> {
            var userId = CurrentToken.subject();
            return communities.directory("", PageRequest.of(0, 20)).getContent().stream()
                    .map(c -> new CommunityView(c.slug(), c.name(), c.memberCount() == null ? 0L : c.memberCount(),
                            memberships.isMember(c.id(), userId)))
                    .toList();
        });
    }

    @McpTool(name = "library.artifactTypes", description = "List the artifact types available for library content, "
            + "so you can supply a valid typeId when contributing.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<ArtifactTypeView> artifactTypes() {
        return support.run("library.artifactTypes", AgentScope.READ, () -> artifactTypes.list().stream()
                .map(t -> new ArtifactTypeView(t.id(), t.label(), t.artifactCount())).toList());
    }

    @McpTool(name = "schedule.read", description = "List a community's sessions between two instants, real sessions "
            + "included even when the whole range is in the past. Use a past window to check what you have already "
            + "backfilled before creating more sessions, since there is no separate import ledger.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<ScheduleView> schedule(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Range start, ISO-8601 instant", required = true) String from,
            @McpToolParam(description = "Range end, ISO-8601 instant", required = true) String to) {
        return support.run("schedule.read", AgentScope.READ,
                () -> schedule.schedule(slug, Instant.parse(from), Instant.parse(to)).stream()
                        .map(e -> new ScheduleView(e.sessionId(), e.title(), e.scheduledAt(), e.durationMinutes(),
                                String.valueOf(e.status()), e.virtual()))
                        .toList());
    }

    @McpTool(name = "session.get", description = "Get one session with its sections and slots, so you can find the "
            + "sectionId and slotId needed to sign up to present.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public SessionView session(@McpToolParam(description = "Session id", required = true) Long id) {
        return support.run("session.get", AgentScope.READ, () -> toSessionView(sessions.get(id)));
    }

    private SessionView toSessionView(Session session) {
        return new SessionView(session.id(), session.title(), session.scheduledAt(), session.durationMinutes(),
                String.valueOf(session.status()), session.sections().stream().map(this::toSectionView).toList());
    }

    private SectionView toSectionView(Section section) {
        return new SectionView(section.id(), section.title(), section.slots().stream().map(this::toSlotView).toList());
    }

    private SlotView toSlotView(Slot slot) {
        return new SlotView(slot.id(), slot.title(), slot.presenterUserIds().isEmpty());
    }

    @McpTool(name = "library.search", description = "Search a community's library. Every filter is optional; supply "
            + "sessionId to see only artifacts attached to one session. Use library.artifactTypes for a valid typeId.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<ArtifactView> librarySearch(
            @McpToolParam(description = "Library search filters", required = true) SearchParams params) {
        return support.run("library.search", AgentScope.READ,
                () -> artifacts.search(params.slug(), toFilter(params), toPageRequest(params)).getContent().stream()
                        .map(ReadTools::toArtifactView).toList());
    }

    private static ArtifactFilter toFilter(SearchParams params) {
        return ArtifactFilter.builder().q(params.q()).typeId(params.typeId()).tag(params.tag())
                .contributor(params.contributor()).from(parseInstant(params.from())).to(parseInstant(params.to()))
                .session(params.sessionId()).sort(params.sort()).build();
    }

    private static PageRequest toPageRequest(SearchParams params) {
        var page = params.page() == null || params.page() < 0 ? 0 : params.page();
        var size = params.size() == null ? 20 : Math.clamp(params.size(), 1, 50);
        return PageRequest.of(page, size);
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static ArtifactView toArtifactView(Artifact artifact) {
        return new ArtifactView(artifact.id(), artifact.title(), artifact.url(), artifact.type(),
                artifact.description(), artifact.tags(), artifact.contributorUserId(), artifact.loveCount(),
                artifact.createdAt());
    }

    @McpTool(name = "session.artifacts", description = "List the artifacts on a session. Pass a row's segmentId and "
            + "artifact.id to session.detachArtifact to remove it. attachmentId is null for an artifact that shows only "
            + "because it came from this session. Use schedule.read or session.get to find the sessionId.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<SessionArtifactView> sessionArtifacts(
            @McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Session id", required = true) Long sessionId) {
        return support.run("session.artifacts", AgentScope.READ,
                () -> resources.resourcesForSession(slug, sessionId).stream()
                        .map(r -> new SessionArtifactView(r.attachmentId(), r.segmentId(), r.attachedByUserId(),
                                toArtifactView(r.artifact())))
                        .toList());
    }

    @McpTool(name = "community.members", description = "List a community's members, so you can place a presenter, "
            + "contributor or proposer whose name could not be matched. Returns user ids and display names, never "
            + "emails. Pass a user id straight back as a presenter, contributor or proposer reference and it resolves "
            + "exactly. You must be a member of the community to read its roster.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<CommunityMemberView> members(@McpToolParam(description = "Community slug", required = true) String slug,
            @McpToolParam(description = "Page, 0-based, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 25, maximum 100", required = false) Integer size) {
        return support.run("community.members", AgentScope.MEMBERS_READ, () -> {
            var communityId = communities.getBySlug(slug).id();
            // The domain floor. The token's scope says what its owner allowed the agent to do; membership says what
            // its owner may see. A roster is exactly what a member already sees in the web app, and nothing more.
            if (!memberships.isMember(communityId, CurrentToken.subject())) {
                throw new ForbiddenException("Community membership required");
            }
            return memberDirectory.list(communityId, memberPage(page, size)).getContent().stream()
                    .map(m -> new CommunityMemberView(m.userId(), m.displayName())).toList();
        });
    }

    /**
     * Built here rather than accepted from the caller: {@code MemberDirectory.list} takes an unvalidated
     * {@link org.springframework.data.domain.Pageable}, so a client-supplied sort would reach the query untouched and a
     * client-supplied size would page a whole organization into memory. The sort is a stable one the caller cannot
     * choose, because a roster paged with no order at all can repeat or skip members between pages.
     */
    private static PageRequest memberPage(Integer page, Integer size) {
        var index = page == null || page < 0 ? 0 : page;
        var pageSize = size == null ? DEFAULT_MEMBER_PAGE_SIZE : Math.clamp(size, 1, MAX_MEMBER_PAGE_SIZE);
        return PageRequest.of(index, pageSize, Sort.by(Sort.Direction.ASC, "user_id"));
    }

    @McpTool(name = "events.sectionTypes", description = "List the section types a session's sections can be built "
            + "from. Get a valid sectionTypeId from here before calling session.addSection. expectsPresenter tells "
            + "you which types hold talk slots.", annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<SectionTypeView> sectionTypes() {
        return support.run("events.sectionTypes", AgentScope.READ, () -> sectionTypes.list().stream()
                .map(t -> new SectionTypeView(t.id(), t.label(), t.expectsPresenter())).toList());
    }
}
