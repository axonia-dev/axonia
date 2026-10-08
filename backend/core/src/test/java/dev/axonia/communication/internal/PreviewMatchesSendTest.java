package dev.axonia.communication.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RecipientDirectory;
import dev.axonia.communication.RecipientDirectory.Presence;
import dev.axonia.communication.RecipientDirectory.Recipient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one test that renders a SINGLE stored row through both {@link OutboxRowProcessor#process} (the real send path)
 * and {@link MessageRenderAdapter#renderEnqueued} (the preview path) and compares the two outputs directly, rather than
 * trusting that reading both classes' source shows them sharing {@link MailRenderer} and {@link FactRehydration}. Every
 * other test in this module proves the two paths share code; this is the one that proves sharing code actually produces
 * the identical subject and HTML an organizer's trust in the preview depends on.
 *
 * <p>
 * It carries a row per previewable message rather than being copied into a parallel class per message, because that is
 * the whole reason the preview endpoint takes the message type instead of gaining a sibling: the guarantee EXTENDS by
 * naming a new message in one place here, rather than being re-established by a second test that would inevitably drift
 * from this one. A third previewable message is one {@link #releasedMessages} entry.
 *
 * <p>
 * <b>Be exact about what a row proves, because the {@code type} column is nearly inert here.</b> Both render paths take
 * their template from the row or message they are handed, so {@code type} only feeds the stubbed
 * {@link JdbcOutbox#findMessageForSubject} lookup. The second row therefore establishes "the {@code session-upcoming}
 * template renders byte-identically through the send path and the preview path, rehydration included" - which is the
 * guarantee worth extending - and NOT "a request naming {@code session_upcoming} is routed to that shared path". The
 * routing is proven elsewhere, deliberately: {@code SessionMessagePreviewerTest} pins the type and the template the
 * previewer hands {@code MessageRenderPort} per message and that the stored-row lookup asks about the right one,
 * {@code SessionReleaseServiceTest} pins wire code to message, and {@code SessionControllerTest} pins the query
 * parameter reaching the service.
 *
 * <p>
 * <b>The guarantee is "the same facts render identically", and the reader's clock is now one of those facts.</b> Both
 * paths resolve it through the same {@link dev.axonia.communication.RecipientDirectory}, for the same member, which is
 * why this test still compares byte for byte: a preview is a render for whoever is reading it, and an organizer
 * previewing a message addressed to themselves sees exactly what lands in their own inbox. It does not, and no longer
 * claims to, guarantee that an organizer sees what a member in another zone will see - that is what the preview dialog
 * saying whose clock it uses is for.
 *
 * <p>
 * Both rows carry the SAME facts and differ only in template, which is deliberate: it isolates the thing under test
 * (one stored fact map, two render paths, identical output) from any difference in what was enqueued. The shared
 * {@code scheduledAt} is what makes each row exercise {@link FactRehydration} - it crosses the jsonb round trip as a
 * plain string and every {@code session-*} template calls {@code atZone} on it - so a repair applied on one path and
 * not the other fails here for both templates rather than only for the one that happened to be tested.
 *
 * <p>
 * {@code unsubscribeTokens.sign} is stubbed to the same fixed token regardless of which user or organization it is
 * called with, so a preview taken by a DIFFERENT member than the row's own recipient still renders byte-identical
 * output here - the footer link legitimately differs by recipient identity and stubbing the token this way isolates
 * that expected, intentional difference from the one this test actually checks: that the CONTENT around it never
 * diverges.
 */
@ExtendWith(MockitoExtension.class)
class PreviewMatchesSendTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String ORG = "acme";
    private static final String FACTS_JSON = """
            {"sessionTitle":"Frontend Dojo","talks":[],"scheduledAt":"2026-08-15T18:30:00Z"}""";

    @Mock
    private JdbcOutbox outbox;
    @Mock
    private DeliveryGate gate;
    @Mock
    private MailBatch batch;
    @Mock
    private RecipientDirectory recipientDirectory;
    @Mock
    private UnsubscribeTokens unsubscribeTokens;
    @Mock
    private ChatDestinationsPort chatDestinations;
    @Mock
    private ChatTransport chatTransport;

    private MailRenderer renderer;
    private PublicUrlProperties publicUrl;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {

        renderer = new MailRenderer(new MailTemplateConfig().mailTemplateEngine());
        publicUrl = new PublicUrlProperties("https://axonia.test");
        jsonMapper = JsonMapper.builder().build();
        when(unsubscribeTokens.sign(any(), any(), any())).thenReturn("shared-token");
    }

    static Stream<Arguments> releasedMessages() {

        return Stream.of(Arguments.of(MessageType.SESSION_WRAP_UP, "session-wrap-up", "Frontend Dojo: what happened"),
                Arguments.of(MessageType.SESSION_UPCOMING, "session-upcoming", "Frontend Dojo: happening soon"));
    }

    @ParameterizedTest
    @MethodSource("releasedMessages")
    void previewAndSendRenderTheIdenticalSubjectAndHtmlForTheSameEnqueuedRow(MessageType type, String template,
            String expectedSubject) {

        // given - one row, claimable by the drain and independently findable by the preview adapter.
        var row = OutboxEntity.builder().id(1L).organizationId(ORG).messageType(type.code()).subjectRef("42")
                .userId("user-1").template(template).facts(FACTS_JSON).status("PENDING").attempts(0).build();

        // when - the real send path.
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(gate.admit(row)).thenReturn(Optional.empty());
        when(recipientDirectory.recipientsOf(ORG, List.of("user-1"))).thenReturn(
                List.of(new Recipient("user-1", "Ada", "ada@example.com", Presence.PRESENT, "Europe/Berlin")));
        var processor = new OutboxRowProcessor(outbox, gate, renderer, recipientDirectory, clock, jsonMapper,
                unsubscribeTokens, publicUrl, chatDestinations, chatTransport);

        processor.process(row, batch);

        var sentCaptor = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(batch).send(sentCaptor.capture());
        var sent = sentCaptor.getValue();

        // when - the preview path, for the message that row was fanned out from.
        var message = MessageEntity.builder().id(1L).organizationId(ORG).messageType(type.code()).subjectRef("42")
                .template(template).facts(FACTS_JSON).build();
        when(outbox.findMessageForSubject(type, "42")).thenReturn(Optional.of(message));
        var adapter = new MessageRenderAdapter(renderer, unsubscribeTokens, publicUrl, outbox, jsonMapper,
                recipientDirectory);

        var previewed = adapter.renderEnqueued(ORG, "user-1", type, "42");

        // then - the two independently-driven renders agree exactly.
        assertThat(previewed).isPresent();
        assertThat(previewed.get().subject()).isEqualTo(sent.subject());
        assertThat(previewed.get().html()).isEqualTo(sent.html());
        assertThat(sent.subject()).isEqualTo(expectedSubject);
        assertThat(sent.html()).contains("15 August 2026");
    }
}
