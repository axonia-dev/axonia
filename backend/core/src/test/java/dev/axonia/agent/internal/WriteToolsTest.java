package dev.axonia.agent.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import dev.axonia.NotFoundException;
import dev.axonia.agent.AgentScope;
import dev.axonia.community.CommunityRole;
import dev.axonia.community.Membership;
import dev.axonia.community.MembershipService;
import dev.axonia.events.ActionItem;
import dev.axonia.events.ActionItemService;
import dev.axonia.events.Attendance;
import dev.axonia.events.AttendanceService;
import dev.axonia.events.CreateOneOffRequest;
import dev.axonia.events.PresenterAssignment;
import dev.axonia.events.PresenterResolution;
import dev.axonia.events.ProposeTopicRequest;
import dev.axonia.events.RaiseActionItemRequest;
import dev.axonia.events.RecordSlotRequest;
import dev.axonia.events.RecordedSlot;
import dev.axonia.events.ScheduledAt;
import dev.axonia.events.Section;
import dev.axonia.events.SectionService;
import dev.axonia.events.SectionTemplate;
import dev.axonia.events.Session;
import dev.axonia.events.SessionService;
import dev.axonia.events.SessionStatus;
import dev.axonia.events.SlotService;
import dev.axonia.events.Topic;
import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicService;
import dev.axonia.knowledge.Artifact;
import dev.axonia.knowledge.ArtifactService;
import dev.axonia.knowledge.AttachResourceRequest;
import dev.axonia.knowledge.Attachment;
import dev.axonia.knowledge.CreateArtifactRequest;
import dev.axonia.knowledge.ResourceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class WriteToolsTest {

    @Mock
    ArtifactService artifacts;
    @Mock
    TopicService topics;
    @Mock
    SlotService slots;
    @Mock
    AttendanceService attendance;
    @Mock
    MembershipService memberships;
    @Mock
    ResourceService resources;
    @Mock
    AgentToolCallRepository auditRepo;
    @Mock
    SessionService sessions;
    @Mock
    SectionService sections;
    @Mock
    ActionItemService actionItems;

    WriteTools sut;

    @BeforeEach
    void setUp() {
        var support = new AgentToolSupport(new AgentScopeGate(), auditRepo,
                Clock.fixed(Instant.parse("2026-07-07T09:00:00Z"), ZoneOffset.UTC));
        sut = new WriteTools(support, artifacts, topics, slots, attendance, memberships, resources, sessions, sections,
                actionItems);
        authenticateWithScopes("library:contribute", "topic:propose", "actionItem:raise", "session:signup",
                "attendance:signal", "community:join");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Replaces the SecurityContext with a token carrying exactly the given scope codes. */
    private void authenticateWithScopes(String... scopes) {
        var jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").claim("org", "default")
                .claim("scopes", List.of(scopes)).claim("tokenId", 7L).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void contribute_buildsRequestAndCallsAndReturnsCompactResult() {
        when(artifacts.capture(eq("kafka-guild"), any(CreateArtifactRequest.class)))
                .thenReturn(Artifact.builder().id(11L).title("Slides").url("https://x.dev/s").build());

        var result = sut.contribute(new WriteTools.ContributeParams("kafka-guild", "Slides", 2L, "https://x.dev/s",
                "notes", Set.of("kafka"), null));

        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.title()).isEqualTo("Slides");
        assertThat(result.url()).isEqualTo("https://x.dev/s");

        var captor = ArgumentCaptor.forClass(CreateArtifactRequest.class);
        verify(artifacts).capture(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Slides");
        assertThat(captor.getValue().typeId()).isEqualTo(2L);
        assertThat(captor.getValue().url()).isEqualTo("https://x.dev/s");
        assertThat(captor.getValue().description()).isEqualTo("notes");
        assertThat(captor.getValue().tags()).containsExactly("kafka");
        assertThat(captor.getValue().provenance()).isNull();
        assertThat(captor.getValue().contributor()).as("an omitted contributor means the caller").isNull();
    }

    /**
     * The point of the whole feature on the library side: an organizer migrating a community's history credits the
     * member whose work it was, and never themselves. The domain resolves the name; the tool's only job is to carry it.
     */
    @Test
    void contribute_carriesTheNamedContributor_soTheMigratorIsNotCreditedForIt() {
        when(artifacts.capture(eq("kafka-guild"), any(CreateArtifactRequest.class)))
                .thenReturn(Artifact.builder().id(11L).title("Slides").url("https://x.dev/s").build());

        sut.contribute(new WriteTools.ContributeParams("kafka-guild", "Slides", 2L, "https://x.dev/s", null, null,
                "Alice Dupont"));

        var captor = ArgumentCaptor.forClass(CreateArtifactRequest.class);
        verify(artifacts).capture(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().contributor()).isEqualTo("Alice Dupont");
    }

    @Test
    void proposeTopic_mapsKindAndReturnsCompactResult() {
        when(topics.propose(eq("kafka-guild"), any(ProposeTopicRequest.class)))
                .thenReturn(Topic.builder().id(5L).title("Streams").kind(TopicKind.REQUEST).build());

        var result = sut.proposeTopic(
                new WriteTools.ProposeTopicParams("kafka-guild", "REQUEST", "Streams", "desc", List.of("kafka"), null));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.title()).isEqualTo("Streams");
        assertThat(result.kind()).isEqualTo("REQUEST");

        var captor = ArgumentCaptor.forClass(ProposeTopicRequest.class);
        verify(topics).propose(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().kind()).isEqualTo(TopicKind.REQUEST);
        assertThat(captor.getValue().title()).isEqualTo("Streams");
        assertThat(captor.getValue().description()).isEqualTo("desc");
        assertThat(captor.getValue().tags()).containsExactly("kafka");
        assertThat(captor.getValue().proposer()).as("an omitted proposer means the caller").isNull();
    }

    @Test
    void proposeTopic_carriesTheNamedProposer_soABackfilledOfferIsNotTheMigratorsOwn() {
        when(topics.propose(eq("kafka-guild"), any(ProposeTopicRequest.class)))
                .thenReturn(Topic.builder().id(5L).title("Streams").kind(TopicKind.OFFER).build());

        sut.proposeTopic(
                new WriteTools.ProposeTopicParams("kafka-guild", "OFFER", "Streams", null, null, "Alice Dupont"));

        var captor = ArgumentCaptor.forClass(ProposeTopicRequest.class);
        verify(topics).propose(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().proposer()).isEqualTo("Alice Dupont");
    }

    /**
     * Action items are raised through ActionItemService now, not proposed as a topic, so this tool must refuse the one
     * kind that used to reach TopicService for that.
     */
    @Test
    void proposeTopic_refusesActionKind_soActionItemsCannotSneakThroughTheTopicService() {
        assertThatThrownBy(() -> sut
                .proposeTopic(new WriteTools.ProposeTopicParams("kafka-guild", "ACTION", "Streams", null, null, null)))
                .isInstanceOf(AgentToolException.class)
                .hasMessage("kind must be REQUEST or OFFER. Use actionItem.raise for operational work.");

        verifyNoInteractions(topics);
    }

    @Test
    void raiseActionItem_buildsRequestAndReturnsCompactResult() {
        when(actionItems.raise(eq("kafka-guild"), any(RaiseActionItemRequest.class)))
                .thenReturn(ActionItem.builder().id(8L).title("Rotate the certificate").assigneeUserId("u-2")
                        .dueDate(LocalDate.parse("2026-10-31")).build());

        var result = sut.raiseActionItem(new WriteTools.RaiseActionItemParams("kafka-guild", "Rotate the certificate",
                "before it expires", List.of("tls"), "Alice Dupont", "2026-10-31", 12L));

        assertThat(result).isEqualTo(new WriteTools.RaiseActionItemResult(8L, "Rotate the certificate", "u-2",
                LocalDate.parse("2026-10-31")));

        var captor = ArgumentCaptor.forClass(RaiseActionItemRequest.class);
        verify(actionItems).raise(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Rotate the certificate");
        assertThat(captor.getValue().description()).isEqualTo("before it expires");
        assertThat(captor.getValue().tags()).containsExactly("tls");
        assertThat(captor.getValue().assignee()).isEqualTo("Alice Dupont");
        assertThat(captor.getValue().dueDate()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(captor.getValue().originSessionId()).isEqualTo(12L);
        assertThat(captor.getValue().checkpointSessionId()).as("an agent-raised item late-binds to the next session")
                .isNull();
    }

    @Test
    void raiseActionItem_withNoDueDateLeavesItUnset() {
        when(actionItems.raise(eq("kafka-guild"), any(RaiseActionItemRequest.class)))
                .thenReturn(ActionItem.builder().id(8L).title("Rotate the certificate").build());

        sut.raiseActionItem(new WriteTools.RaiseActionItemParams("kafka-guild", "Rotate the certificate", null, null,
                null, null, null));

        var captor = ArgumentCaptor.forClass(RaiseActionItemRequest.class);
        verify(actionItems).raise(eq("kafka-guild"), captor.capture());
        assertThat(captor.getValue().dueDate()).isNull();
    }

    @Test
    void raiseActionItem_refusesAMalformedDueDateWithoutCallingTheService() {
        assertThatThrownBy(() -> sut.raiseActionItem(new WriteTools.RaiseActionItemParams("kafka-guild", "Anything",
                null, null, null, "next tuesday", null))).isInstanceOf(AgentToolException.class)
                .hasMessage("dueDate must be an ISO date, yyyy-MM-dd.");

        verifyNoInteractions(actionItems);
    }

    @Test
    void signUp_callsServiceAndEchoesCoordinates() {
        when(slots.signUp(1L, 2L, 3L)).thenReturn(Session.builder().id(1L).build());

        var result = sut.signUp(1L, 2L, 3L);

        assertThat(result.sessionId()).isEqualTo(1L);
        assertThat(result.sectionId()).isEqualTo(2L);
        assertThat(result.slotId()).isEqualTo(3L);
        verify(slots).signUp(1L, 2L, 3L);
    }

    @Test
    void signalAttendance_returnsCompactResult() {
        var createdAt = Instant.parse("2026-07-07T09:00:00Z");
        when(attendance.signal(1L))
                .thenReturn(Attendance.builder().id(9L).sessionId(1L).userId("u-1").createdAt(createdAt).build());

        var result = sut.signalAttendance(1L);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.sessionId()).isEqualTo(1L);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        verify(attendance).signal(1L);
    }

    @Test
    void joinCommunity_returnsCompactResult() {
        var joinedAt = Instant.parse("2026-07-07T09:00:00Z");
        when(memberships.join("kafka-guild")).thenReturn(Membership.builder().id(4L).communityId(3L).userId("u-1")
                .role(CommunityRole.MEMBER).joinedAt(joinedAt).build());

        var result = sut.joinCommunity("kafka-guild");

        assertThat(result.id()).isEqualTo(4L);
        assertThat(result.communityId()).isEqualTo(3L);
        assertThat(result.role()).isEqualTo("MEMBER");
        assertThat(result.joinedAt()).isEqualTo(joinedAt);
        verify(memberships).join("kafka-guild");
    }

    @Test
    void attachArtifact_delegatesAndMapsResult() {
        authenticateWithScopes("session:attach");
        when(resources.attach("platform-guild", 42L, new AttachResourceRequest(null, 11L))).thenReturn(Attachment
                .builder().id(7L).artifactId(11L).sessionId(42L).segmentId(null).attachedByUserId("u-1").build());

        var result = sut.attachArtifact("platform-guild", 42L, null, 11L);

        assertThat(result.attachmentId()).isEqualTo(7L);
        assertThat(result.sessionId()).isEqualTo(42L);
        assertThat(result.artifactId()).isEqualTo(11L);
        assertThat(result.segmentId()).isNull();
        verify(resources).attach("platform-guild", 42L, new AttachResourceRequest(null, 11L));
    }

    @Test
    void attachArtifact_refusedWithoutSessionAttachScope() {
        authenticateWithScopes("read", "library:contribute");

        assertThatThrownBy(() -> sut.attachArtifact("platform-guild", 42L, null, 11L))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("session:attach");
        verifyNoInteractions(resources);
    }

    /**
     * The five write tools that map 1:1 to their own service, paired with the scope each one must require.
     * {@code session:attach} / {@code session:detach} are deliberately excluded: both map to the same {@code resources}
     * service, so a per-scope {@code verifyNoInteractions(serviceFor(scope))} would be ambiguous between them. They are
     * pinned instead by their own dedicated tests. The four {@code schedule:manage} tools are excluded for the same
     * reason, doubly so: one scope covers four tools, and two of them ({@code session.addSlot}, {@code
     * slot.addPresenter}) share the {@code slots} service with {@code session.signup}. They too are pinned by their own
     * dedicated tests below.
     */
    private static final AgentScope[] WRITE_SCOPES = {AgentScope.LIBRARY_CONTRIBUTE, AgentScope.TOPIC_PROPOSE,
            AgentScope.ACTION_ITEM_RAISE, AgentScope.SESSION_SIGNUP, AgentScope.ATTENDANCE_SIGNAL,
            AgentScope.COMMUNITY_JOIN};

    static Stream<Arguments> writeTools() {
        return Arrays.stream(WRITE_SCOPES).map(Arguments::of);
    }

    /**
     * Pins each of the six 1:1 tools to its own scope: granting the OTHER five scopes but not the tool's own must deny
     * the call. This is the property that would break if a tool were wired to the wrong scope (the happy-path tests
     * grant every scope and so cannot catch such a mis-wiring).
     */
    @ParameterizedTest
    @MethodSource("writeTools")
    void tool_isDeniedWhenTokenLacksItsOwnScope_andNeverCallsService(AgentScope ownScope) {
        var otherScopes = Arrays.stream(WRITE_SCOPES).filter(s -> s != ownScope).map(AgentScope::code)
                .toArray(String[]::new);
        authenticateWithScopes(otherScopes);

        assertThatThrownBy(() -> invoke(ownScope)).isInstanceOf(AgentToolException.class);
        verifyNoInteractions(serviceFor(ownScope));
    }

    private void invoke(AgentScope scope) {
        Consumer<WriteTools> action = switch (scope) {
            case LIBRARY_CONTRIBUTE -> tools -> tools.contribute(
                    new WriteTools.ContributeParams("kafka-guild", "Slides", 2L, "https://x.dev/s", null, null, null));
            case TOPIC_PROPOSE -> tools -> tools.proposeTopic(
                    new WriteTools.ProposeTopicParams("kafka-guild", "REQUEST", "Streams", null, null, null));
            case ACTION_ITEM_RAISE -> tools -> tools.raiseActionItem(
                    new WriteTools.RaiseActionItemParams("kafka-guild", "Anything", null, null, null, null, null));
            case SESSION_SIGNUP -> tools -> tools.signUp(1L, 2L, 3L);
            case ATTENDANCE_SIGNAL -> tools -> tools.signalAttendance(1L);
            case COMMUNITY_JOIN -> tools -> tools.joinCommunity("kafka-guild");
            default -> throw new IllegalArgumentException("not a write tool: " + scope);
        };
        action.accept(sut);
    }

    private Object serviceFor(AgentScope scope) {
        return switch (scope) {
            case LIBRARY_CONTRIBUTE -> artifacts;
            case TOPIC_PROPOSE -> topics;
            case ACTION_ITEM_RAISE -> actionItems;
            case SESSION_SIGNUP -> slots;
            case ATTENDANCE_SIGNAL -> attendance;
            case COMMUNITY_JOIN -> memberships;
            default -> throw new IllegalArgumentException("not a write tool: " + scope);
        };
    }

    @Test
    void detachArtifact_delegatesAndEchoesWhatItRemoved() {
        authenticateWithScopes("session:detach");

        var result = sut.detachArtifact("kafka-guild", 100L, 7L, 9L);

        assertThat(result).isEqualTo(new WriteTools.DetachResult(100L, 9L, 7L));
        verify(resources).detach("kafka-guild", 100L, 9L, 7L);
    }

    @Test
    void detachArtifact_sessionLevelRow_passesNoSegment() {
        authenticateWithScopes("session:detach");

        sut.detachArtifact("kafka-guild", 100L, null, 9L);

        verify(resources).detach("kafka-guild", 100L, 9L, null);
    }

    @Test
    void detachArtifact_refusedWithAttachScopeOnly() {
        authenticateWithScopes("read", "session:attach");

        assertThatThrownBy(() -> sut.detachArtifact("kafka-guild", 100L, 7L, 9L)).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("session:detach");
        verifyNoInteractions(resources);
    }

    @Test
    void detachArtifact_mapsNotOnTheSessionToAgentSafeMessage() {
        authenticateWithScopes("session:detach");
        doThrow(new NotFoundException("Artifact is not on this session: 9")).when(resources).detach("kafka-guild", 100L,
                9L, 7L);

        assertThatThrownBy(() -> sut.detachArtifact("kafka-guild", 100L, 7L, 9L)).isInstanceOf(AgentToolException.class)
                .hasMessage("That item was not found.");
    }

    private static Session aSession() {
        return Session.builder().id(42L).title("Guild #42").build();
    }

    private static Session sessionWith(Long id, String title, Instant scheduledAt, SessionStatus status) {
        return Session.builder().id(id).title(title).scheduledAt(scheduledAt).status(status).build();
    }

    private static Session sessionWithSection(Long sectionId, String sectionTitle) {
        return Session.builder().id(42L)
                .sections(List.of(Section.builder().id(sectionId).sectionTypeId(3L).title(sectionTitle).build()))
                .build();
    }

    @Test
    void createSession_createsAOneOffAtAPastDate_forBackfill() {

        authenticateWithScopes("schedule:manage");
        var pastDate = Instant.parse("2025-03-14T17:00:00Z");
        given(sessions.createOneOff(eq("guild"), any(CreateOneOffRequest.class)))
                .willReturn(sessionWith(42L, "Guild #42", pastDate, SessionStatus.HELD));

        var result = sut
                .createSession(new WriteTools.CreateSessionParams("guild", "Guild #42", "2025-03-14T17:00:00Z", 60));

        assertThat(result.sessionId()).isEqualTo(42L);
        assertThat(result.status()).isEqualTo("HELD");
    }

    /**
     * The one behavioural conversion in this tool. Its own parameter is an absolute ISO instant; the request the events
     * context now takes is a civil date, a wall clock and the zone that clock is read in. The three have to compose
     * back to exactly the moment the caller named, so the whole triple is asserted and then round-tripped through
     * {@code ZoneId.of} - a conversion that read the clock in the machine's own zone while still labelling it
     * {@code UTC} would produce a perfectly plausible-looking date here and silently move every backfilled session.
     */
    @Test
    void createSession_statesTheAgentsInstantAsTheWallClockAndZoneItAlreadyWas() {

        authenticateWithScopes("schedule:manage");
        var named = Instant.parse("2025-03-14T17:00:00Z");
        given(sessions.createOneOff(eq("guild"), any(CreateOneOffRequest.class)))
                .willReturn(sessionWith(42L, "Guild #42", named, SessionStatus.HELD));

        sut.createSession(new WriteTools.CreateSessionParams("guild", "Guild #42", "2025-03-14T17:00:00Z", 60));

        var captor = ArgumentCaptor.forClass(CreateOneOffRequest.class);
        verify(sessions).createOneOff(eq("guild"), captor.capture());
        var at = captor.getValue().scheduledAt();
        assertThat(ZonedDateTime.of(at.date(), at.time(), ZoneId.of(at.zone())).toInstant()).isEqualTo(named);
        assertThat(at).isEqualTo(new ScheduledAt(LocalDate.of(2025, 3, 14), LocalTime.of(17, 0), "UTC"));
    }

    @Test
    void createSession_refusedWithoutScheduleManageScope() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut
                .createSession(new WriteTools.CreateSessionParams("guild", "Guild #42", "2025-03-14T17:00:00Z", 60)))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("schedule:manage");
        verifyNoInteractions(sessions);
    }

    @Test
    void addSection_addsASectionToASession() {

        authenticateWithScopes("schedule:manage");
        given(sections.addSection(eq(42L), any(SectionTemplate.class))).willReturn(sessionWithSection(7L, "Talks"));

        var result = sut.addSection(42L, 3L, "Talks", 30);

        assertThat(result.sectionId()).isEqualTo(7L);
    }

    @Test
    void addSection_refusedWithoutScheduleManageScope() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut.addSection(42L, 3L, "Talks", 30)).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("schedule:manage");
        verifyNoInteractions(sections);
    }

    @Test
    void addSlot_recordsTheNamedPresenters_andReportsTheOnesItCouldNotPlace() {

        authenticateWithScopes("schedule:manage");
        given(slots.recordSlot(eq(42L), eq(7L), any(RecordSlotRequest.class))).willReturn(
                new RecordedSlot(aSession(), 9L, PresenterResolution.builder().assigned(List.of("Alice Dupont"))
                        .unmatched(List.of("J. Smith")).ambiguous(List.of("Bob")).build()));

        var result = sut.addSlot(new WriteTools.AddSlotParams(42L, 7L, "Kafka 101", 15, null,
                List.of("Alice Dupont", "J. Smith", "Bob"), null));

        assertThat(result.slotId()).isEqualTo(9L);
        assertThat(result.assigned()).containsExactly("Alice Dupont");
        assertThat(result.unmatched()).containsExactly("J. Smith");
        assertThat(result.ambiguous()).containsExactly("Bob");

        // The single most important behaviour of this tool: it must record the NAMED presenters, never the caller.
        // addSlot() would silently credit whoever ran the migration; recordSlot() is the only path that resolves the
        // free-text names instead.
        verify(slots).recordSlot(eq(42L), eq(7L), any(RecordSlotRequest.class));
        verify(slots, never()).addSlot(any(), any(), any());
    }

    /**
     * A recorded slot was never proposed, so it has no topic to inherit a subject from. Without its own tags it teaches
     * the expertise graph nothing, and a backfilled history stays invisible to it.
     */
    @Test
    void addSlot_carriesTheTags_soABackfilledTalkHasASubject() {

        authenticateWithScopes("schedule:manage");
        given(slots.recordSlot(eq(42L), eq(7L), any(RecordSlotRequest.class)))
                .willReturn(new RecordedSlot(aSession(), 9L, PresenterResolution.builder()
                        .assigned(List.of("Alice Dupont")).unmatched(List.of()).ambiguous(List.of()).build()));

        sut.addSlot(new WriteTools.AddSlotParams(42L, 7L, "Kafka 101", 15, null, List.of("Alice Dupont"),
                List.of("kafka", "streaming")));

        var captor = ArgumentCaptor.forClass(RecordSlotRequest.class);
        verify(slots).recordSlot(eq(42L), eq(7L), captor.capture());
        assertThat(captor.getValue().tags()).containsExactly("kafka", "streaming");
    }

    @Test
    void addSlot_withoutTags_recordsAnUntaggedSlotRatherThanFailing() {

        authenticateWithScopes("schedule:manage");
        given(slots.recordSlot(eq(42L), eq(7L), any(RecordSlotRequest.class)))
                .willReturn(new RecordedSlot(aSession(), 9L, PresenterResolution.empty()));

        sut.addSlot(new WriteTools.AddSlotParams(42L, 7L, "Kafka 101", 15, null, List.of(), null));

        var captor = ArgumentCaptor.forClass(RecordSlotRequest.class);
        verify(slots).recordSlot(eq(42L), eq(7L), captor.capture());
        assertThat(captor.getValue().tags()).isEmpty();
    }

    @Test
    void addSlot_refusedWithoutScheduleManageScope() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut
                .addSlot(new WriteTools.AddSlotParams(42L, 7L, "Kafka 101", 15, null, List.of("Alice Dupont"), null)))
                .isInstanceOf(AgentToolException.class).hasMessageContaining("schedule:manage");
        verifyNoInteractions(slots);
    }

    @Test
    void addPresenter_reportsAnUnplaceableName_ratherThanFailing() {

        authenticateWithScopes("schedule:manage");
        given(slots.addPresenter(42L, 7L, 9L, "J. Smith"))
                .willReturn(new PresenterAssignment(aSession(), false, PresenterAssignment.NOT_FOUND, 0));

        var result = sut.addPresenter(42L, 7L, 9L, "J. Smith");

        assertThat(result.assigned()).isFalse();
        assertThat(result.reason()).isEqualTo("NOT_FOUND");
        assertThat(result.candidates()).isZero();
    }

    @Test
    void addPresenter_tellsTheAgentHowManyMembersAnAmbiguousNameMatched() {

        // An agent told only "AMBIGUOUS" cannot tell two namesakes from a query that was simply too vague, and so
        // cannot decide whether to ask a human or just try a fuller name.
        authenticateWithScopes("schedule:manage");
        given(slots.addPresenter(42L, 7L, 9L, "Bob"))
                .willReturn(new PresenterAssignment(aSession(), false, PresenterAssignment.AMBIGUOUS, 2));

        var result = sut.addPresenter(42L, 7L, 9L, "Bob");

        assertThat(result.assigned()).isFalse();
        assertThat(result.reason()).isEqualTo("AMBIGUOUS");
        assertThat(result.candidates()).isEqualTo(2);
    }

    @Test
    void addPresenter_refusedWithoutScheduleManageScope() {
        authenticateWithScopes("read");

        assertThatThrownBy(() -> sut.addPresenter(42L, 7L, 9L, "J. Smith")).isInstanceOf(AgentToolException.class)
                .hasMessageContaining("schedule:manage");
        verifyNoInteractions(slots);
    }
}
