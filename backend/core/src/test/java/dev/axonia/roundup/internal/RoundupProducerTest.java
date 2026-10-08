package dev.axonia.roundup.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import dev.axonia.OrganizationResolver;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.communication.ProductionWatermark;
import dev.axonia.identity.UserDirectory;
import dev.axonia.organization.Organization;
import dev.axonia.organization.Organizations;
import dev.axonia.roundup.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.RoundupAvailabilityPolicy;
import dev.axonia.roundup.RoundupSettingsService;
import dev.axonia.roundup.SessionActivity.HeldSession;
import dev.axonia.roundup.internal.QuarterActivity.NamedCommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The guards, in the order they run, and the floor. Every early return is its own test: a guard that silently stopped
 * guarding would otherwise cost a tenant a quarter's roundup - or send one it had switched off - with nothing failing
 * anywhere to say so.
 */
@MockitoSettings
@ExtendWith(OutputCaptureExtension.class)
class RoundupProducerTest {

    private static final String ORG = "acme";
    private static final String ORG_NAME = "Acme";
    private static final Instant NOW = Instant.parse("2026-07-10T09:00:00Z");
    private static final Instant QUARTER_END = Instant.parse("2026-07-01T00:00:00Z");
    private static final Duration SETTLE_LAG = Duration.ofDays(2);
    private static final int COMMUNITY_CAP = 8;
    private static final int SESSIONS_PER_COMMUNITY_CAP = 4;
    private static final int TOPIC_CAP = 12;
    private static final Quarter QUARTER = new Quarter(2026, 2);
    private static final HeldSession HELD = HeldSession.builder().sessionId(1L).communityId(7L)
            .title("Hexagons in anger").heldAt(Instant.parse("2026-05-12T17:00:00Z")).talkCount(1).talkTitles(List.of())
            .presenterNames(List.of("Alice Dubois")).build();

    @Mock
    private OrganizationResolver organizationResolver;
    @Mock
    private Organizations organizations;
    @Mock
    private RoundupSettingsService settings;
    @Mock
    private RoundupFactsAssembler assembler;
    @Mock
    private UserDirectory directory;
    @Mock
    private CommunicationPort port;
    @Mock
    private ProductionWatermark watermark;

    private RoundupProducer producer;

    private Boolean availableHere;

    @BeforeEach
    void buildProducer() {

        RoundupAvailabilityPolicy policy = organizationId -> Optional.ofNullable(availableHere);
        producer = new RoundupProducer(organizationResolver, organizations, new RoundupAvailability(List.of(policy)),
                settings, assembler, directory, port, watermark,
                new RoundupProperties(SETTLE_LAG, COMMUNITY_CAP, SESSIONS_PER_COMMUNITY_CAP, TOPIC_CAP));
        lenient().when(organizationResolver.currentOrganization()).thenReturn(Optional.of(ORG));
        // Lenient: the guards before it deliberately return without ever asking the watermark, so stubbing it
        // eagerly would make those tests fail on an unused stubbing rather than on what they assert.
        lenient().when(watermark.since(anyString(), any())).thenReturn(Instant.EPOCH);
    }

    /** Asked before the registry, so an organization the roundup is not available to costs no lookup of its own. */
    @Test
    void producesNothingForAnOrganizationTheRoundupIsNotAvailableTo() {

        availableHere = false;

        producer.produce(NOW);

        verifyNoInteractions(organizations, settings, assembler, directory, port, watermark);
    }

    /**
     * The sweep hands over every organization id it can read, and an id that resolves to nothing is a tenant this
     * process knows nothing about. Fails closed rather than assuming an enterprise.
     */
    @Test
    void producesNothingForAnOrganizationItCannotResolve() {

        given(organizations.byId(ORG)).willReturn(Optional.empty());

        producer.produce(NOW);

        verifyNoInteractions(settings, assembler, directory, port, watermark);
    }

    @Test
    void producesNothingWhenTheRoundupIsSwitchedOff() {

        given(organizations.byId(ORG)).willReturn(Optional.of(organization(true)));
        given(settings.enabledFor(ORG)).willReturn(false);

        producer.produce(NOW);

        verifyNoInteractions(assembler, directory, port, watermark);
    }

    /**
     * The kill switch stops what LEAVES the product, and it is {@code DeliveryGate} that stops it - per channel, on the
     * row, with a recorded {@code ORGANIZATION_OFF}. Refusing to enqueue at all is not a cheaper way of saying the same
     * thing: there is then no message, no {@code IN_APP} delivery, and nothing for the gate's in-app track - which
     * admits a row an organization has silenced outbound - to admit. A tenant that has switched outbound mail off is
     * precisely the tenant whose in-app record is the only record there is.
     *
     * <p>
     * The damage is not one missed quarter either. The watermark advances below, on the same tick, so a quarter that
     * was never enqueued is still claimed and never re-derived. Returning before it left the watermark where it was,
     * and {@code since} then admitted only the most recently closed quarter once the switch came back on - every
     * quarter silenced in between was permanently absent from every member's panel, with nothing anywhere recording
     * why.
     */
    @Test
    void stillProducesWhileTheOrganizationHasOutboundCommunicationsSwitchedOff() {

        given(organizations.byId(ORG)).willReturn(Optional.of(organization(false)));
        given(settings.enabledFor(ORG)).willReturn(true);
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice"));

        producer.produce(NOW);

        var enqueued = ArgumentCaptor.forClass(OutboundMessage.class);
        then(port).should().enqueue(enqueued.capture());
        assertThat(enqueued.getValue().type()).isEqualTo(MessageType.ORGANIZATION_ROUNDUP);
        assertThat(enqueued.getValue().recipientUserIds()).containsExactly("alice");
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
    }

    /**
     * A session held on the last day of the quarter is routinely closed out a day or two later, and its wrap-up carries
     * the presenters the roundup names. Producing the instant the quarter ends would report the quarter as it looked
     * before its own last week was written up.
     */
    @Test
    void doesNotProduceUntilTheSettleLagHasPassed() {

        givenAnEnabledEnterprise();

        producer.produce(QUARTER_END.plus(SETTLE_LAG).minusSeconds(1));

        then(port).should(never()).enqueue(any());
        then(watermark).should(never()).advanceTo(anyString(), any());
        // Pins guard 4 ahead of guard 5, which only an unsettled quarter can show: the settle guard is arithmetic on
        // the instant already in hand, so an unsettled tick must return before the watermark is ever read.
        then(watermark).should(never()).since(anyString(), any());
        verifyNoInteractions(assembler, directory);
    }

    /**
     * The boundary is inclusive - a quarter that has settled for exactly the lag has settled. Asserted because the test
     * above only pins the instant before it, so {@code isBefore} could turn into {@code isAfter} negated, or the
     * comparison could lose a nanosecond, and nothing would notice.
     */
    @Test
    void producesAtTheExactInstantTheSettleLagExpires() {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice"));

        producer.produce(QUARTER_END.plus(SETTLE_LAG));

        then(port).should().enqueue(any());
    }

    /** The watermark is what makes the next 24,000 ticks of the same quarter cost one settings read and one lookup. */
    @Test
    void neverProducesAQuarterItHasAlreadyClaimed() {

        givenAnEnabledEnterprise();
        given(watermark.since(RoundupProducer.PRODUCER_KEY, Instant.EPOCH)).willReturn(QUARTER_END);

        producer.produce(NOW);

        then(port).should(never()).enqueue(any());
        then(watermark).should(never()).advanceTo(anyString(), any());
        verifyNoInteractions(assembler, directory);
    }

    @Test
    void aQuarterBelowTheActivityFloorEnqueuesNothingButIsStillSkippedOnce() {

        givenAnEnabledEnterprise();
        given(assembler.read(ORG, ORG_NAME, QUARTER)).willReturn(activity().build());

        producer.produce(NOW);

        then(port).should(never()).enqueue(any());
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
        verifyNoInteractions(directory);
    }

    /**
     * The floor and the letter are one judgement, and this is the quarter that tells them apart. Everything was held in
     * a community archived before the produce tick fired, so the session port still answers a full list while
     * {@link RoundupFacts#of} - which drops any session whose community {@code activeCommunities} no longer names -
     * assembles a letter reporting nothing at all.
     *
     * <p>
     * A floor read off the raw port result passes here, and every present employee is mailed "No sessions were held
     * over the quarter." and nothing else, which is forbidden outright. The first-timer count is set deliberately: it
     * comes from an organization-wide port with no community predicate, so the same letter would also have read "3
     * people presented for the first time." one line below the sentence saying none were held.
     */
    @Test
    void aQuarterWhoseOnlyCommunityWasArchivedIsBelowTheFloorTheLetterWouldReport() {

        givenAnEnabledEnterprise();
        given(assembler.read(ORG, ORG_NAME, QUARTER))
                .willReturn(activity().communities(List.of()).held(List.of(HELD)).firstTimePresenterCount(3).build());

        producer.produce(NOW);

        then(port).should(never()).enqueue(any());
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
        verifyNoInteractions(directory);
    }

    /**
     * The floor used to be either signal, sessions OR capture, and the capture clause is now redundant rather than
     * dropped: {@code ArtifactActivity} is asked which artifacts hang off THIS quarter's held sessions, so a quarter
     * the letter can name no session in has nothing captured either. Kept as the guard on that reasoning - a reading
     * that somehow answered artifacts with no sessions to hang them off is still a letter that can name nothing, and
     * must not be mailed to every present employee.
     */
    @Test
    void aQuarterWithNoSessionsIsBelowTheFloorHoweverMuchThePortSaysWasCaptured() {

        givenAnEnabledEnterprise();
        given(assembler.read(ORG, ORG_NAME, QUARTER)).willReturn(activity().captured(capturedArtifacts(3)).build());

        producer.produce(NOW);

        then(port).should(never()).enqueue(any());
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
        verifyNoInteractions(directory);
    }

    @Test
    void enqueuesOneRoundupAddressedToEveryPresentMember() {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice", "bob", "carol"));

        producer.produce(NOW);

        var enqueued = ArgumentCaptor.forClass(OutboundMessage.class);
        then(port).should().enqueue(enqueued.capture());
        assertThat(enqueued.getValue().type()).isEqualTo(MessageType.ORGANIZATION_ROUNDUP);
        assertThat(enqueued.getValue().subjectRef()).isEqualTo("2026-Q2");
        assertThat(enqueued.getValue().recipientUserIds()).containsExactly("alice", "bob", "carol");
        assertThat(enqueued.getValue().template()).isEqualTo("organization-roundup");
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
    }

    /**
     * The facts are frozen into the outbox row here and are what every recipient is eventually shown, so an empty map
     * is not a placeholder to fill in later: the templates interpolate the headline counts with no null guard of their
     * own, and an organization-wide mail would render the literal word "null" where a number belongs.
     */
    @Test
    void aProducedRoundupCarriesTheQuartersRealFacts() {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice"));

        producer.produce(NOW);

        var enqueued = ArgumentCaptor.forClass(OutboundMessage.class);
        then(port).should().enqueue(enqueued.capture());
        assertThat(enqueued.getValue().facts()).containsEntry("sessionCount", 1).containsEntry("talkCount", 1)
                .containsEntry("presenterCount", 1).containsEntry("resourceCount", 0)
                .containsEntry("sessionTitle", "Q2 2026");
        assertThat(enqueued.getValue().facts().get("communities").toString()).contains("Backend Guild")
                .contains("Alice Dubois");
    }

    /**
     * The producer and {@link RoundupPreviewer} must render the same quarter identically, which they can only do by
     * assembling through the one {@link RoundupFacts#of} call rather than each shaping a map of its own.
     */
    @Test
    void theEnqueuedFactsAreExactlyWhatTheOneAssemblyProduces() {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice"));

        producer.produce(NOW);

        var enqueued = ArgumentCaptor.forClass(OutboundMessage.class);
        then(port).should().enqueue(enqueued.capture());
        assertThat(enqueued.getValue().facts()).isEqualTo(RoundupFacts.of(activity().held(List.of(HELD)).build()));
    }

    /**
     * The watermark advances here too - it has to, or every tick for the next three months re-derives the same quarter
     * - so this quarter is skipped once and permanently, and the log line is the only trace left of it. Usually it
     * means exactly what it says, a tenant with nobody present. It is also the precise shape an {@code identity_user}
     * scoping fault takes while the session and artifact ports still answer normally, which the scope guard below
     * cannot catch: that guard reads the {@code SecurityContext}, not the GUC the connection was actually given.
     */
    @Test
    void anActiveQuarterNobodyIsPresentForIsLoggedRatherThanSilentlySkipped(CapturedOutput output) {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of());

        producer.produce(NOW);

        then(port).should(never()).enqueue(any());
        then(watermark).should().advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
        assertThat(output.getOut()).contains("2026-Q2").contains(ORG).contains("reached nobody");
    }

    /**
     * The guards are an order, not a set. Each one above proves its own early return, and every one of them would still
     * pass with the order shuffled; this is what pins the sequence itself, including the last two steps - the enqueue
     * commits before the watermark moves past the quarter it covers, never after.
     */
    @Test
    void runsTheGuardsAndTheAssemblyInTheOrderTheSpecFixes() {

        givenAnEnabledEnterprise();
        givenAQuarterWithActivity();
        given(directory.presentUserIds(ORG)).willReturn(List.of("alice"));

        producer.produce(NOW);

        var inOrder = inOrder(organizations, settings, watermark, assembler, directory, port);
        inOrder.verify(organizations).byId(ORG);
        inOrder.verify(settings).enabledFor(ORG);
        inOrder.verify(watermark).since(RoundupProducer.PRODUCER_KEY, Instant.EPOCH);
        inOrder.verify(assembler).read(ORG, ORG_NAME, QUARTER);
        inOrder.verify(directory).presentUserIds(ORG);
        inOrder.verify(port).enqueue(any());
        inOrder.verify(watermark).advanceTo(RoundupProducer.PRODUCER_KEY, NOW);
    }

    /**
     * The one failure mode the activity ports cannot report themselves: unscoped, every one of them answers an empty
     * list or a zero under the {@code axonia_app} role rather than failing, so a tick that lost its
     * {@code OrganizationScope} would read a dead quarter, advance the watermark past it and send nothing, forever,
     * with no error anywhere. Resolving the organization from the scope itself is what turns that into a refusal.
     */
    @Test
    void refusesToRunOutsideAnOrganizationScope() {

        given(organizationResolver.currentOrganization()).willReturn(Optional.empty());

        assertThatThrownBy(() -> producer.produce(NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Organization scope");
        verifyNoInteractions(organizations, settings, assembler, directory, port, watermark);
    }

    private void givenAnEnabledEnterprise() {

        given(organizations.byId(ORG)).willReturn(Optional.of(organization(true)));
        given(settings.enabledFor(ORG)).willReturn(true);
    }

    private void givenAQuarterWithActivity() {

        given(assembler.read(ORG, ORG_NAME, QUARTER)).willReturn(activity().held(List.of(HELD)).build());
    }

    private static QuarterActivity.QuarterActivityBuilder activity() {

        return QuarterActivity.builder().organizationName(ORG_NAME).quarter(QUARTER)
                .communities(List.of(new NamedCommunity(7L, "Backend Guild", null, null))).held(List.of())
                .upcoming(List.of()).firstTimePresenterCount(0).captured(List.of()).topics(List.of()).openTalkCount(0)
                .communityCap(COMMUNITY_CAP).sessionsPerCommunityCap(SESSIONS_PER_COMMUNITY_CAP).topicCap(TOPIC_CAP)
                .communitiesUrl("https://axonia.example/communities");
    }

    private static List<CapturedArtifact> capturedArtifacts(int count) {

        return IntStream.range(0, count)
                .mapToObj(i -> new CapturedArtifact(1L, "Artifact " + i, "https://example.test/" + i, "Recording"))
                .toList();
    }

    private static Organization organization(boolean communicationsEnabled) {

        return Organization.builder().id(ORG).name(ORG_NAME).communicationsEnabled(communicationsEnabled).build();
    }
}
