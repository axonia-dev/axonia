package dev.axonia.events.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.MessageRenderPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RenderRequest;
import dev.axonia.communication.RenderedMessage;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SessionMessagePreviewer} tries {@link MessageRenderPort#renderEnqueued} first and only falls back to
 * assembling facts live - through the exact call the message's own producer makes - when no row is enqueued yet. These
 * tests prove both halves for both message types: the live path captures the exact {@link RenderRequest} the port
 * receives and asserts its {@code facts} is the very same map {@link SessionFactsAssembler} returned - not an
 * equal-looking copy, not a hand-built substitute; the stored path proves {@code factsAssembler} is never even called
 * once a row exists, which is what makes editing the session after enqueue unable to change what the preview shows.
 *
 * <p>
 * The two message types assemble through DIFFERENT overloads, and each test names which one it expects: the reminder
 * through {@link SessionFactsAssembler#facts}, which resolves neither {@code nextSession} nor {@code actionPoints}, and
 * the wrap-up through {@link SessionFactsAssembler#factsForWrapUp}, which resolves both. {@code @MockitoSettings}'
 * strict stubbing is what makes that discriminating - a previewer assembling the reminder through the wrap-up's
 * overload calls a method the reminder's test never stubbed, and fails rather than quietly receiving null.
 */
@MockitoSettings
class SessionMessagePreviewerTest {

    private static final String ORGANIZATION_ID = "acme";
    private static final Instant NOW = Instant.parse("2026-08-14T18:00:00Z");

    @Mock
    private SessionFactsAssembler factsAssembler;

    @Mock
    private MessageRenderPort renderPort;

    private SessionMessagePreviewer previewer;

    @BeforeEach
    void wire() {

        previewer = new SessionMessagePreviewer(factsAssembler, renderPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void noWrapUpRowYetRendersThroughTheSameFactsTheProducerWouldAssemble() {

        // given
        var entity = SessionEntityMother.any().id(42L).build();
        var facts = Map.<String, Object>of("sessionTitle", "Frontend Dojo");
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_WRAP_UP, "42"))
                .thenReturn(Optional.empty());
        when(factsAssembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW)).thenReturn(facts);
        when(renderPort.render(any())).thenReturn(new RenderedMessage("Frontend Dojo: what happened", "<html></html>"));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entity, "user-1", ReleasableMessage.WRAP_UP);

        // then
        var request = capturedRequest();
        assertThat(request.facts()).isSameAs(facts);
        assertThat(request.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.type()).isEqualTo(MessageType.SESSION_WRAP_UP);
        assertThat(request.template()).isEqualTo("session-wrap-up");
        assertThat(preview.subject()).isEqualTo("Frontend Dojo: what happened");
        assertThat(preview.html()).isEqualTo("<html></html>");
    }

    /**
     * The core claim for the reminder: preview and send are the same call. {@link UpcomingSessionProducer} enqueues
     * {@link SessionFactsAssembler#facts} against template {@code session-upcoming}, and so does this - the captured
     * request is the assertion, not the rendered output.
     */
    @Test
    void noReminderRowYetRendersThroughTheSameFactsTheUpcomingProducerWouldAssemble() {

        // given
        var entity = SessionEntityMother.any().id(42L).build();
        var facts = Map.<String, Object>of("sessionTitle", "Frontend Dojo");
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_UPCOMING, "42"))
                .thenReturn(Optional.empty());
        when(factsAssembler.facts(ORGANIZATION_ID, entity)).thenReturn(facts);
        when(renderPort.render(any())).thenReturn(new RenderedMessage("Frontend Dojo is coming up", "<html></html>"));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entity, "user-1", ReleasableMessage.UPCOMING);

        // then
        var request = capturedRequest();
        assertThat(request.facts()).isSameAs(facts);
        assertThat(request.type()).isEqualTo(MessageType.SESSION_UPCOMING);
        assertThat(request.template()).isEqualTo("session-upcoming");
        assertThat(preview.subject()).isEqualTo("Frontend Dojo is coming up");
    }

    /**
     * A session that raised no talks and no action points still assembles a (mostly empty) fact map rather than
     * throwing - {@link SessionFactsAssemblerTest} already proves {@code factsForWrapUp} tolerates that; this proves
     * {@link SessionMessagePreviewer} passes such a map straight through rather than special-casing it.
     */
    @Test
    void aSessionWithNoWrapUpContentStillRendersRatherThanThrowing() {

        // given
        var entity = SessionEntityMother.any().id(42L).build();
        var sparseFacts = Map.<String, Object>of("sessionTitle", "Frontend Dojo", "talks", List.of());
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_WRAP_UP, "42"))
                .thenReturn(Optional.empty());
        when(factsAssembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW)).thenReturn(sparseFacts);
        when(renderPort.render(any())).thenReturn(new RenderedMessage("Frontend Dojo: what happened", "<html></html>"));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entity, "user-1", ReleasableMessage.WRAP_UP);

        // then
        assertThat(preview.subject()).isEqualTo("Frontend Dojo: what happened");
    }

    /**
     * Once a row is enqueued, {@code renderEnqueued} answers directly and {@code factsAssembler} is never consulted -
     * proving the branch actually short-circuits rather than merely returning the right value by coincidence.
     */
    @Test
    void aRowAlreadyEnqueuedRendersTheStoredFactsWithoutTouchingTheFactsAssembler() {

        // given
        var entity = SessionEntityMother.any().id(42L).build();
        var stored = new RenderedMessage("Frontend Dojo: what happened", "<html>stored</html>");
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_WRAP_UP, "42"))
                .thenReturn(Optional.of(stored));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entity, "user-1", ReleasableMessage.WRAP_UP);

        // then
        assertThat(preview.subject()).isEqualTo(stored.subject());
        assertThat(preview.html()).isEqualTo(stored.html());
        verifyNoInteractions(factsAssembler);
    }

    /**
     * The stored-row rule carries over to the reminder unchanged, and it matters at least as much here as it does for
     * the wrap-up: an organizer can still reschedule a session after its reminder was enqueued, and the frozen row
     * announces the OLD instant to everybody who receives it. A live-facts preview would show the new one and quietly
     * disagree with every mail already on its way.
     */
    @Test
    void aReminderAlreadyEnqueuedRendersTheStoredFactsWithoutTouchingTheFactsAssembler() {

        // given
        var entity = SessionEntityMother.any().id(42L).build();
        var stored = new RenderedMessage("Frontend Dojo is coming up", "<html>the instant we announced</html>");
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_UPCOMING, "42"))
                .thenReturn(Optional.of(stored));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entity, "user-1", ReleasableMessage.UPCOMING);

        // then
        assertThat(preview.html()).isEqualTo("<html>the instant we announced</html>");
        verifyNoInteractions(factsAssembler);
    }

    /**
     * The direct proof: an organizer editing the session's follow-up notes AFTER the wrap-up was enqueued must not
     * change what the preview shows - {@code factsAssembler} would reflect the edit if it were ever called, so the
     * assertion that it stays uncalled is the whole guarantee. Modelled here by simply never stubbing
     * {@code factsAssembler} at all; {@code @MockitoSettings}' strict stubbing means any call to it that this test did
     * not anticipate fails loudly rather than quietly returning {@code null}.
     */
    @Test
    void editingTheSessionAfterEnqueueDoesNotChangeWhatThePreviewShows() {

        // given - entityAtPreviewTime stands in for a session whose follow-up notes were edited after enqueue; the
        // stored row's own facts (opaque here, resolved through the mocked port) still carry the ORIGINAL notes.
        var entityAtPreviewTime = SessionEntityMother.any().id(42L).notes("Edited after the wrap-up was queued")
                .build();
        var storedAtEnqueueTime = new RenderedMessage("Frontend Dojo: what happened", "<html>original notes</html>");
        when(renderPort.renderEnqueued(ORGANIZATION_ID, "user-1", MessageType.SESSION_WRAP_UP, "42"))
                .thenReturn(Optional.of(storedAtEnqueueTime));

        // when
        var preview = previewer.preview(ORGANIZATION_ID, entityAtPreviewTime, "user-1", ReleasableMessage.WRAP_UP);

        // then
        assertThat(preview.html()).isEqualTo("<html>original notes</html>");
        verifyNoInteractions(factsAssembler);
    }

    private RenderRequest capturedRequest() {

        var captor = ArgumentCaptor.forClass(RenderRequest.class);
        verify(renderPort).render(captor.capture());
        return captor.getValue();
    }
}
