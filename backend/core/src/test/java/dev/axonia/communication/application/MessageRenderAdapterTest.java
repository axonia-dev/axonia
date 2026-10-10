package dev.axonia.communication.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.communication.adapter.out.persistence.JdbcOutbox;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.RenderRequest;
import dev.axonia.communication.api.spi.RecipientDirectory;
import dev.axonia.communication.configuration.MailTemplateConfig;
import dev.axonia.communication.domain.OutboxMessage;
import dev.axonia.shared.configuration.PublicUrlProperties;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageRenderAdapter} renders through a real {@link MailRenderer} - the same instance shape
 * {@link OutboxDrainTest} exercises with a mock, but here left real, so these tests prove the adapter's HTML is what
 * the actual Thymeleaf templates produce, not what a stub says it produces.
 */
@ExtendWith(MockitoExtension.class)
class MessageRenderAdapterTest {

    @Mock
    private UnsubscribeTokens unsubscribeTokens;
    @Mock
    private JdbcOutbox outbox;
    @Mock
    private RecipientDirectory recipientDirectory;

    private MessageRenderAdapter adapter;

    @BeforeEach
    void setUp() {

        var renderer = new MailRenderer(new MailTemplateConfig().mailTemplateEngine());
        adapter = new MessageRenderAdapter(renderer, unsubscribeTokens, new PublicUrlProperties("https://axonia.test"),
                outbox, JsonMapper.builder().build(), recipientDirectory);
    }

    @Test
    void rendersThroughTheRealMailRendererWithTheRequestedTemplateAndFacts() {

        // given
        when(unsubscribeTokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");
        var request = RenderRequest.builder().organizationId("acme").userId("user-1").type(MessageType.SESSION_WRAP_UP)
                .template("session-wrap-up").facts(Map.of("sessionTitle", "Frontend Dojo", "talks", List.of())).build();

        // when
        var rendered = adapter.render(request);

        // then - matches MailRendererTest's own assertion for the identical facts, proving this adapter is not a
        // parallel rendering path.
        assertThat(rendered.subject()).isEqualTo("Frontend Dojo: what happened");
        assertThat(rendered.html()).contains("No talks were recorded for this session.");
    }

    @Test
    void mintsTheUnsubscribeFooterLinkForThePreviewingMemberAndFoldsItIntoTheFacts() {

        // given
        when(unsubscribeTokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");
        var request = RenderRequest.builder().organizationId("acme").userId("user-1").type(MessageType.SESSION_WRAP_UP)
                .template("session-wrap-up").facts(Map.of("sessionTitle", "Frontend Dojo", "talks", List.of())).build();

        // when
        var rendered = adapter.render(request);

        // then
        verify(unsubscribeTokens).sign("user-1", "acme", MessageType.SESSION_WRAP_UP);
        assertThat(rendered.html()).contains("https://axonia.test/unsubscribe?token=signed-token");
    }

    @Test
    void neverMutatesTheCallersFactsMap() {

        // given - an immutable Map.of(): a mutating implementation would throw UnsupportedOperationException here
        // before ever reaching the renderer.
        when(unsubscribeTokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");
        var facts = Map.<String, Object>of("sessionTitle", "Frontend Dojo", "talks", List.of());
        var request = RenderRequest.builder().organizationId("acme").userId("user-1").type(MessageType.SESSION_WRAP_UP)
                .template("session-wrap-up").facts(facts).build();

        // when
        adapter.render(request);

        // then
        assertThat(facts).doesNotContainKey("unsubscribePageUrl");
    }

    @Test
    void renderEnqueuedIsEmptyWhenNoRowExistsYet() {

        // given
        when(outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "42")).thenReturn(Optional.empty());

        // when
        var rendered = adapter.renderEnqueued("acme", "user-1", MessageType.SESSION_WRAP_UP, "42");

        // then
        assertThat(rendered).isEmpty();
    }

    /**
     * The stored message names no recipient at all - it is shared by every one of them - so the token can only be
     * minted for the previewing member ({@code user-1}), exactly as
     * {@link #mintsTheUnsubscribeFooterLinkForThePreviewingMemberAndFoldsItIntoTheFacts} already proves for the live
     * path. Before the message and its deliveries were split apart this needed a second, negative verification against
     * the stored row's own recipient; there is no longer a recipient on the message for the adapter to reach for.
     */
    @Test
    void renderEnqueuedRendersTheStoredMessagesFactsAndMintsTheTokenForThePreviewingMember() {

        // given
        var message = OutboxMessage.builder().template("session-wrap-up")
                .facts("{\"sessionTitle\":\"Frontend Dojo\",\"talks\":[]}").build();
        when(outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "42")).thenReturn(Optional.of(message));
        when(unsubscribeTokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");

        // when
        var rendered = adapter.renderEnqueued("acme", "user-1", MessageType.SESSION_WRAP_UP, "42");

        // then
        assertThat(rendered).isPresent();
        assertThat(rendered.get().subject()).isEqualTo("Frontend Dojo: what happened");
        assertThat(rendered.get().html()).contains("https://axonia.test/unsubscribe?token=signed-token");
        verify(unsubscribeTokens).sign("user-1", "acme", MessageType.SESSION_WRAP_UP);
    }

    /**
     * The stored message's {@code scheduledAt} is a plain ISO-8601 string, exactly what a real jsonb round trip leaves
     * behind - not a real {@code Instant}. If {@link MessageRenderAdapter#renderEnqueued} skipped
     * {@link FactRehydration}, this would fail the same way an unrehydrated real send does: a
     * {@code TemplateProcessingException} reaching for {@code String.atZone}, not a wrong date in the output.
     */
    @Test
    void renderEnqueuedRehydratesTheStoredMessagesTemporalFactsBeforeRendering() {

        // given
        var message = OutboxMessage.builder().template("session-wrap-up")
                .facts("{\"sessionTitle\":\"Frontend Dojo\",\"talks\":[],\"scheduledAt\":\"2026-08-15T18:30:00Z\"}")
                .build();
        when(outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "42")).thenReturn(Optional.of(message));
        when(unsubscribeTokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP)).thenReturn("signed-token");

        // when
        var rendered = adapter.renderEnqueued("acme", "user-1", MessageType.SESSION_WRAP_UP, "42");

        // then
        assertThat(rendered).isPresent();
        assertThat(rendered.get().html()).contains("15 August 2026");
    }
}
