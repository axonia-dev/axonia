package dev.axonia.communication.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.MessageRenderPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.RenderRequest;
import dev.axonia.communication.api.RenderedMessage;
import dev.axonia.communication.api.spi.RecipientDirectory;
import dev.axonia.communication.application.port.out.Outbox;
import dev.axonia.communication.domain.OutboxMessage;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one implementation of {@link MessageRenderPort}. {@link #render} mints the requesting recipient's own unsubscribe
 * footer link, folds it into a copy of the caller's facts, and renders through {@link MailRenderer} - the exact steps
 * {@link OutboxRowProcessor#compose} performs before a real send, in the exact same order, sharing the exact same
 * {@link UnsubscribeLinks} constants, so a preview and a send can never disagree about what a fact map turns into.
 * {@link #renderEnqueued} does the same for a row that already exists in the outbox, reading its stored facts back
 * through {@link FactRehydration} - the identical repair {@code OutboxRowProcessor#compose} applies to a row it is
 * about to actually mail - rather than through anything assembled fresh.
 *
 * <p>
 * {@link #render}'s live path never touches a {@code JsonMapper} or crosses a jsonb round trip:
 * {@link RenderRequest#facts} arrives as whatever the caller already holds in memory - real {@code Instant} and
 * {@code LocalDate} objects for a wrap-up's {@code scheduledAt} and action point due dates. That is precisely why it
 * cannot, on its own, prove a preview matches what a real send would later produce for the identical facts once they
 * are written to jsonb and read back - a future third temporal fact would render correctly here and still break the
 * real mail, exactly as {@code scheduledAt} and {@code actionPoints[].dueDate} both once did before
 * {@link FactRehydration} existed. {@link #renderEnqueued} is what closes that gap once a row exists to close it
 * against: it replays the real jsonb round trip a send takes, through the identical
 * {@link FactRehydration#parse(JsonMapper, String)} call {@code OutboxRowProcessor#compose} makes, so a preview taken
 * after enqueue is not just "close" but a literal replay of the send path minus the SMTP hop.
 */
@Component
@RequiredArgsConstructor
public class MessageRenderAdapter implements MessageRenderPort {

    private final MailRenderer renderer;
    private final UnsubscribeTokens unsubscribeTokens;
    private final PublicUrls publicUrl;
    private final Outbox outbox;
    private final JsonMapper jsonMapper;
    private final RecipientDirectory recipientDirectory;

    @Override
    public RenderedMessage render(RenderRequest request) {

        var facts = new LinkedHashMap<>(request.facts());
        facts.put(UnsubscribeLinks.FACT_KEY,
                unsubscribePageUrl(request.organizationId(), request.userId(), request.type()));
        facts.put(MomentLine.READER_ZONE_FACT, timeZoneOf(request.organizationId(), request.userId()));
        var rendered = renderer.render(request.template(), facts);
        return new RenderedMessage(rendered.subject(), rendered.html());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RenderedMessage> renderEnqueued(String organizationId, String previewingUserId, MessageType type,
            String subjectRef) {

        return outbox.findMessageForSubject(type, subjectRef)
                .map(message -> renderStoredRow(message, organizationId, previewingUserId, type));
    }

    private RenderedMessage renderStoredRow(OutboxMessage message, String organizationId, String previewingUserId,
            MessageType type) {

        var facts = FactRehydration.parse(jsonMapper, message.getFacts());
        facts.put(UnsubscribeLinks.FACT_KEY, unsubscribePageUrl(organizationId, previewingUserId, type));
        facts.put(MomentLine.READER_ZONE_FACT, timeZoneOf(organizationId, previewingUserId));
        var rendered = renderer.render(message.getTemplate(), facts);
        return new RenderedMessage(rendered.subject(), rendered.html());
    }

    /**
     * The previewing organizer's own clock, resolved through the same directory a real send resolves the recipient's
     * through. A preview is a render for a real reader rather than for nobody, and the one person it is certainly being
     * read by is whoever asked for it - so it states their hour and the preview dialog says whose it is.
     *
     * <p>
     * Null when they have no clock, which lands the render on the session's own, exactly as it would for a recipient
     * nobody has measured.
     */
    private String timeZoneOf(String organizationId, String userId) {

        return recipientDirectory.recipientsOf(organizationId, List.of(userId)).stream().findFirst()
                .map(RecipientDirectory.Recipient::timeZone).orElse(null);
    }

    private String unsubscribePageUrl(String organizationId, String userId, MessageType type) {

        var token = unsubscribeTokens.sign(userId, organizationId, type);
        return UnsubscribeLinks.pageUrl(publicUrl.publicBaseUrl(), token);
    }
}
