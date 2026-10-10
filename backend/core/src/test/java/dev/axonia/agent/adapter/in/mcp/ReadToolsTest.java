package dev.axonia.agent.adapter.in.mcp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.agent.application.ToolCallRecorder;
import dev.axonia.agent.application.port.out.ToolCallAudit;
import dev.axonia.agent.domain.ToolCall;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.community.api.CommunityMember;
import dev.axonia.community.api.MemberDirectory;
import dev.axonia.community.api.Memberships;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.Schedules;
import dev.axonia.events.api.Section;
import dev.axonia.events.api.SectionType;
import dev.axonia.events.api.SectionTypes;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.Slot;
import dev.axonia.knowledge.api.Artifact;
import dev.axonia.knowledge.api.ArtifactFilter;
import dev.axonia.knowledge.api.ArtifactType;
import dev.axonia.knowledge.api.ArtifactTypes;
import dev.axonia.knowledge.api.Artifacts;
import dev.axonia.knowledge.api.Resources;
import dev.axonia.knowledge.api.SessionResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ReadToolsTest {

    @Mock
    Communities communities;
    @Mock
    Memberships memberships;
    @Mock
    Schedules schedule;
    @Mock
    Sessions sessions;
    @Mock
    ArtifactTypes artifactTypes;
    @Mock
    ToolCallAudit auditRepo;
    @Mock
    Artifacts artifacts;
    @Mock
    Resources resources;
    @Mock
    SectionTypes sectionTypes;
    @Mock
    MemberDirectory memberDirectory;

    ReadTools sut;

    @BeforeEach
    void setUp() {
        var support = new AgentToolSupport(new AgentScopeGate(),
                new ToolCallRecorder(auditRepo, Clock.fixed(Instant.parse("2026-07-07T09:00:00Z"), ZoneOffset.UTC)));
        sut = new ReadTools(support, communities, memberships, artifactTypes, schedule, sessions, artifacts, resources,
                sectionTypes, memberDirectory);
        withScopes("read");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Replaces the SecurityContext with a token carrying exactly the given scope codes. */
    private void withScopes(String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of(scopes)).claim("tokenId", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void listCommunities_returnsSlugsWithMembershipFlag() {
        var c = Community.builder().id(3L).slug("kafka-guild").name("Kafka Guild").memberCount(12L).build();
        var noCount = Community.builder().id(4L).slug("empty-guild").name("Empty Guild").memberCount(null).build();
        when(communities.directory(any(), any())).thenReturn(new PageImpl<>(List.of(c, noCount)));
        when(memberships.isMember(eq(3L), eq("u-1"))).thenReturn(true);

        var result = sut.listCommunities();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).satisfies(v -> {
            assertThat(v.slug()).isEqualTo("kafka-guild");
            assertThat(v.name()).isEqualTo("Kafka Guild");
            assertThat(v.memberCount()).isEqualTo(12L);
            assertThat(v.member()).isTrue();
        });
        assertThat(result.get(1)).satisfies(v -> {
            assertThat(v.slug()).isEqualTo("empty-guild");
            assertThat(v.memberCount()).isEqualTo(0L);
            assertThat(v.member()).isFalse();
        });
    }

    @Test
    void artifactTypes_mapsToCompactView() {
        when(artifactTypes.list())
                .thenReturn(List.of(ArtifactType.builder().id(1L).label("Slides").artifactCount(4).build()));

        assertThat(sut.artifactTypes()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(1L);
            assertThat(v.label()).isEqualTo("Slides");
            assertThat(v.artifactCount()).isEqualTo(4L);
        });
    }

    @Test
    void schedule_mapsEntriesToCompactView() {
        var entry = ScheduleEntry.builder().sessionId(9L).title("Kafka Deep Dive")
                .scheduledAt(Instant.parse("2026-07-10T18:00:00Z")).hasTime(true).durationMinutes(60)
                .status(SessionStatus.SCHEDULED).virtual(false).build();
        when(schedule.schedule(eq("kafka-guild"), eq(Instant.parse("2026-07-10T00:00:00Z")),
                eq(Instant.parse("2026-07-17T00:00:00Z")))).thenReturn(List.of(entry));

        var result = sut.schedule("kafka-guild", "2026-07-10T00:00:00Z", "2026-07-17T00:00:00Z");

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.sessionId()).isEqualTo(9L);
            assertThat(v.title()).isEqualTo("Kafka Deep Dive");
            assertThat(v.scheduledAt()).isEqualTo(Instant.parse("2026-07-10T18:00:00Z"));
            assertThat(v.durationMinutes()).isEqualTo(60);
            assertThat(v.status()).isEqualTo("SCHEDULED");
            assertThat(v.virtual()).isFalse();
        });
    }

    @Test
    void session_mapsToCompactViewWithSectionsAndSlots() {
        var openSlot = Slot.builder().id(21L).title("Intro").presenterUserIds(List.of()).build();
        var takenSlot = Slot.builder().id(22L).title("Deep Dive").presenterUserIds(List.of("u-9")).build();
        var section = Section.builder().id(11L).title("Talk").slots(List.of(openSlot, takenSlot)).build();
        var session = Session.builder().id(5L).title("Kafka Deep Dive")
                .scheduledAt(Instant.parse("2026-07-10T18:00:00Z")).durationMinutes(60).status(SessionStatus.SCHEDULED)
                .sections(List.of(section)).build();
        when(sessions.get(5L)).thenReturn(session);

        var result = sut.session(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.title()).isEqualTo("Kafka Deep Dive");
        assertThat(result.scheduledAt()).isEqualTo(Instant.parse("2026-07-10T18:00:00Z"));
        assertThat(result.durationMinutes()).isEqualTo(60);
        assertThat(result.status()).isEqualTo("SCHEDULED");
        assertThat(result.sections()).singleElement().satisfies(sv -> {
            assertThat(sv.sectionId()).isEqualTo(11L);
            assertThat(sv.title()).isEqualTo("Talk");
            assertThat(sv.slots()).hasSize(2);
            assertThat(sv.slots().get(0).slotId()).isEqualTo(21L);
            assertThat(sv.slots().get(0).title()).isEqualTo("Intro");
            assertThat(sv.slots().get(0).open()).isTrue();
            assertThat(sv.slots().get(1).slotId()).isEqualTo(22L);
            assertThat(sv.slots().get(1).open()).isFalse();
        });
    }

    private static Artifact artifact() {
        return Artifact.builder().id(11L).title("Retry patterns").url("https://example.com/retries").type("Talk")
                .description("A talk").tags(List.of("resilience")).contributorUserId("u-9").loveCount(3)
                .createdAt(Instant.parse("2026-07-01T10:00:00Z")).build();
    }

    @Test
    void librarySearch_mapsEveryFilterFieldAndPaging() {
        when(artifacts.search(eq("platform-guild"), any(ArtifactFilter.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artifact())));

        var params = new ReadTools.SearchParams("platform-guild", "retries", 3L, "resilience", "u-9",
                "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z", 42L, "mostAppreciated", 1, 5);
        var result = sut.librarySearch(params);

        var filter = ArgumentCaptor.forClass(ArtifactFilter.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artifacts).search(eq("platform-guild"), filter.capture(), pageable.capture());

        assertThat(filter.getValue().q()).isEqualTo("retries");
        assertThat(filter.getValue().typeId()).isEqualTo(3L);
        assertThat(filter.getValue().tag()).isEqualTo("resilience");
        assertThat(filter.getValue().contributor()).isEqualTo("u-9");
        assertThat(filter.getValue().from()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(filter.getValue().to()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(filter.getValue().session()).isEqualTo(42L);
        assertThat(filter.getValue().sort()).isEqualTo("mostAppreciated");
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(1, 5));

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo(11L);
            assertThat(v.title()).isEqualTo("Retry patterns");
            assertThat(v.url()).isEqualTo("https://example.com/retries");
            assertThat(v.tags()).containsExactly("resilience");
            assertThat(v.loveCount()).isEqualTo(3);
        });
    }

    @Test
    void librarySearch_appliesDefaultsAndClampsPageSize() {
        when(artifacts.search(any(), any(ArtifactFilter.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        sut.librarySearch(new ReadTools.SearchParams("platform-guild", null, null, null, null, null, null, null, null,
                null, 999));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artifacts).search(any(), any(ArtifactFilter.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 50));
    }

    @Test
    void librarySearch_treatsBlankInstantsAsAbsent() {
        when(artifacts.search(any(), any(ArtifactFilter.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        sut.librarySearch(
                new ReadTools.SearchParams("platform-guild", null, null, null, null, "  ", "", null, null, null, null));

        var filter = ArgumentCaptor.forClass(ArtifactFilter.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artifacts).search(any(), filter.capture(), pageable.capture());
        assertThat(filter.getValue().from()).isNull();
        assertThat(filter.getValue().to()).isNull();
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20));
    }

    @Test
    void librarySearch_coercesNegativePageToZero() {
        when(artifacts.search(any(), any(ArtifactFilter.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        sut.librarySearch(
                new ReadTools.SearchParams("platform-guild", null, null, null, null, null, null, null, null, -3, 10));

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(artifacts).search(any(), any(ArtifactFilter.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 10));
    }

    @Test
    void sessionArtifacts_mapsAttachmentMetadataAndNestedArtifact() {
        when(resources.resourcesForSession("platform-guild", 42L)).thenReturn(List.of(SessionResource.builder()
                .attachmentId(7L).segmentId(2L).attachedByUserId("u-3").artifact(artifact()).build()));

        var result = sut.sessionArtifacts("platform-guild", 42L);

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.attachmentId()).isEqualTo(7L);
            assertThat(v.segmentId()).isEqualTo(2L);
            assertThat(v.attachedByUserId()).isEqualTo("u-3");
            assertThat(v.artifact().id()).isEqualTo(11L);
            assertThat(v.artifact().title()).isEqualTo("Retry patterns");
        });
    }

    @Test
    void sessionArtifacts_returnsEmptyWhenNothingAttached() {
        when(resources.resourcesForSession("platform-guild", 99L)).thenReturn(List.of());
        assertThat(sut.sessionArtifacts("platform-guild", 99L)).isEmpty();
    }

    @Test
    void sectionTypes_listsTheOrganizationsSectionTypes() {

        withScopes("read");
        given(sectionTypes.list()).willReturn(
                List.of(SectionType.builder().id(3L).label("Lightning Talk").expectsPresenter(true).build()));

        var result = sut.sectionTypes();

        assertThat(result).containsExactly(new ReadTools.SectionTypeView(3L, "Lightning Talk", true));
    }

    @Test
    void sectionTypes_isRefusedWithoutTheReadScope() {

        withScopes();

        assertThatThrownBy(() -> sut.sectionTypes()).isInstanceOf(AgentToolException.class);
        then(sectionTypes).shouldHaveNoInteractions();
    }

    private void aCommunityTheCallerBelongsTo() {
        given(communities.getBySlug("kafka-guild"))
                .willReturn(Community.builder().id(3L).slug("kafka-guild").name("Kafka Guild").build());
        given(memberships.isMember(3L, "u-1")).willReturn(true);
    }

    @Test
    void members_listTheRosterWithDisplayNames_soAnUnplaceableNameCanBePlaced() {

        withScopes("members:read");
        aCommunityTheCallerBelongsTo();
        given(memberDirectory.list(eq(3L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(new CommunityMember("u-9", "Alice Dupont"))));

        var result = sut.members("kafka-guild", null, null);

        assertThat(result).containsExactly(new ReadTools.CommunityMemberView("u-9", "Alice Dupont"));
    }

    @Test
    void members_defaultsToTheFirstPageOfTwentyFive() {

        withScopes("members:read");
        aCommunityTheCallerBelongsTo();
        given(memberDirectory.list(eq(3L), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        sut.members("kafka-guild", null, null);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberDirectory).list(eq(3L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
    }

    /**
     * MemberDirectory.list takes an unvalidated Pageable, so the tool builds its own rather than passing anything the
     * client hands it straight through: a page size of 5000 would page a whole organization into memory.
     */
    @Test
    void members_capsThePageSizeAtAHundred_andCoercesANegativePage() {

        withScopes("members:read");
        aCommunityTheCallerBelongsTo();
        given(memberDirectory.list(eq(3L), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        sut.members("kafka-guild", -3, 5000);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(memberDirectory).list(eq(3L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    /**
     * The scope ceiling: reading the schedule and reading the roster are different decisions, so {@code read} does not
     * confer {@code members:read}. The refusal must also leave an audit row behind.
     */
    @Test
    void members_isRefusedWithoutTheMembersReadScope_andTheAuditRowRecordsIt() {

        withScopes("read");

        assertThatThrownBy(() -> sut.members("kafka-guild", null, null)).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("members:read");

        then(memberDirectory).shouldHaveNoInteractions();
        then(communities).shouldHaveNoInteractions();
        var audited = ArgumentCaptor.forClass(ToolCall.class);
        verify(auditRepo).record(audited.capture());
        assertThat(audited.getValue().toolName()).isEqualTo("community.members");
        assertThat(audited.getValue().outcome()).isEqualTo("SCOPE_DENIED");
    }

    /** The domain floor: the scope lets the token ask, membership is what lets its owner see. */
    @Test
    void members_isRefusedForACommunityTheCallerDoesNotBelongTo() {

        withScopes("members:read");
        given(communities.getBySlug("kafka-guild")).willReturn(Community.builder().id(3L).slug("kafka-guild").build());
        given(memberships.isMember(3L, "u-1")).willReturn(false);

        assertThatThrownBy(() -> sut.members("kafka-guild", null, null)).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("not a member of this community");

        then(memberDirectory).shouldHaveNoInteractions();
    }
}
