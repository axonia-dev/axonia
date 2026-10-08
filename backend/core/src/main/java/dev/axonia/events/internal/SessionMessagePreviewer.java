package dev.axonia.events.internal;

import java.time.Clock;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageRenderPort;
import dev.axonia.communication.RenderRequest;
import dev.axonia.communication.RenderedMessage;
import dev.axonia.events.MessagePreview;
import lombok.RequiredArgsConstructor;

/**
 * Renders one of a session's {@link ReleasableMessage}s for the one organizer looking at it right now. Once that
 * message's producer has enqueued it, its facts are frozen in the outbox at the instant they were enqueued - from that
 * point on, THAT frozen row is "the message" in every sense that matters to a recipient, sent or not yet sent, so
 * {@link #preview} renders it through {@link MessageRenderPort#renderEnqueued} rather than re-deriving facts live. Only
 * when no row exists yet does it fall back to {@link #facts}, which makes the exact call that message's own producer
 * makes - {@link SessionFactsAssembler#facts} for the reminder, as {@link UpcomingSessionProducer} does;
 * {@link SessionFactsAssembler#factsForWrapUp} for the wrap-up, as {@link WrapUpProducer} does - and hands the result
 * to {@link MessageRenderPort#render} untouched. Nothing here rebuilds, trims or otherwise reshapes either fact map:
 * the whole point is that a preview which diverges from the real message is worthless, and the surest way to guarantee
 * that is to never give this class a second way to produce facts of its own.
 *
 * <p>
 * The {@code switch} over {@link ReleasableMessage} is exhaustive with no {@code default}, which is what makes the
 * endpoint taking a message type safe to extend: a third releasable message would fail to compile here rather than
 * silently inheriting one of these two assemblies.
 *
 * <p>
 * Rendering the frozen row instead of live facts once one exists is not a minor refinement: the release card offers
 * preview in {@code QUEUED} and {@code SENT}, both states reachable only after enqueue, and an organizer can still edit
 * the session - its follow-up notes, its schedule - at that point. A live-facts preview taken then would show the
 * EDITED session while every recipient who already received, or is about to receive, the frozen row sees the ORIGINAL
 * one: a preview that lies about a send, in the one feature built to stop exactly that.
 *
 * <p>
 * {@code SessionReleaseService} is the one caller, and it alone is responsible for finding the session and enforcing
 * {@code canManage} before this method ever runs - a rendered message carries another member's display name and
 * whatever the organizer wrote in the follow-up notes, so this class trusts its caller to have already answered "is
 * this person allowed to see that" rather than asking again itself.
 */
@Component
@RequiredArgsConstructor
public class SessionMessagePreviewer {

    private final SessionFactsAssembler factsAssembler;
    private final MessageRenderPort renderPort;
    private final Clock clock;

    public MessagePreview preview(String organizationId, SessionEntity entity, String userId,
            ReleasableMessage message) {

        var subjectRef = String.valueOf(entity.getId());
        var rendered = renderPort.renderEnqueued(organizationId, userId, message.type(), subjectRef)
                .orElseGet(() -> renderLive(organizationId, entity, userId, message));
        return new MessagePreview(rendered.subject(), rendered.html());
    }

    private RenderedMessage renderLive(String organizationId, SessionEntity entity, String userId,
            ReleasableMessage message) {

        var request = RenderRequest.builder().organizationId(organizationId).userId(userId).type(message.type())
                .template(message.template()).facts(facts(organizationId, entity, message)).build();
        return renderPort.render(request);
    }

    /**
     * The reminder never mentions what follows the session, so it assembles through the overload that resolves neither
     * {@code nextSession} nor {@code actionPoints} and needs no clock at all. Only the wrap-up's assembly reads
     * {@code now}, to find the series' next occurrence after this one.
     */
    private Map<String, Object> facts(String organizationId, SessionEntity entity, ReleasableMessage message) {

        return switch (message) {
            case UPCOMING -> factsAssembler.facts(organizationId, entity);
            case WRAP_UP -> factsAssembler.factsForWrapUp(organizationId, entity, clock.instant());
        };
    }
}
