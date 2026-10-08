package dev.axonia.roundup.internal;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.MessageRenderPort;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.RenderRequest;
import dev.axonia.communication.RenderedMessage;
import lombok.RequiredArgsConstructor;

/**
 * Renders the most recently closed quarter's roundup for the one administrator looking at it, following the rule
 * {@code SessionMessagePreviewer} already established: once a quarter's message has been enqueued, its facts are frozen
 * in the outbox at that instant, and from then on THAT row is the message in every sense that matters to a recipient,
 * sent or not. So {@link MessageRenderPort#renderEnqueued} comes first, and the live-facts fallback runs only where
 * there is nothing frozen yet to be faithful to.
 *
 * <p>
 * The fallback assembles through the same {@link RoundupFactsAssembler} and the same {@link RoundupFacts#of} call
 * {@code RoundupProducer} makes, and reshapes nothing: a preview that diverges from the send it stands in for is worse
 * than no preview, and the surest way to prevent that is to leave this class no way of producing facts of its own.
 *
 * <p>
 * <b>It deliberately applies no activity floor, unlike the producer.</b> {@code RoundupProducer} returns without
 * enqueuing when a quarter held not one session it can still name, so for such a quarter this renders a complete
 * message - its zero arm, the sentence "No sessions were held over the quarter." standing in for the whole number band,
 * with the first-timer line suppressed under it - that no recipient will ever receive. That is the right answer rather
 * than an oversight, and the reason is not the session preview's precedent, which has no floor to transfer: <b>the
 * previewed quarter is already, in the ordinary case, one that will never be sent.</b> The roundup ships off, the
 * preview exists to inform the decision to switch it on, and the most recently closed quarter has by then already gone
 * unsent because the switch was off. Refusing on the floor while rendering happily for a switched-off tenant would
 * single out one of the two reasons a quarter goes unsent and act on only that one, which is less coherent, not more
 * honest. An administrator asking "what does this look like" gets an answer either way, and the one case where the
 * preview must be exact about a specific send - a row already enqueued - is the {@code renderEnqueued} path above,
 * which is unaffected. Pinned by {@code RoundupPreviewerTest.rendersAQuarterBelowTheProducersActivityFloor}.
 *
 * <p>
 * {@code @Transactional} even though nothing here writes, for the reason {@code SessionReleaseService#preview}'s
 * javadoc gives in full: {@link MessageRenderPort#renderEnqueued} decides whether a row exists by reading through a
 * connection scoped by the {@code app.current_org} GUC rather than by the {@code organizationId} it is handed, and an
 * unscoped read there answers "no row" instead of failing - which would silently route every preview into the
 * live-facts fallback, the one path that can differ from what recipients were actually sent. One transaction around the
 * whole method means that lookup and the reads around it share the exact connection the request scoped when it was
 * opened.
 */
@Component
@RequiredArgsConstructor
public class RoundupPreviewer {

    private static final String TEMPLATE = "organization-roundup";

    private final RoundupFactsAssembler assembler;
    private final MessageRenderPort renderPort;
    private final Clock clock;

    @Transactional
    public RenderedMessage preview(String organizationId, String organizationName, String viewingUserId) {

        var quarter = Quarter.mostRecentlyClosed(clock.instant());
        return renderPort.renderEnqueued(organizationId, viewingUserId, MessageType.ORGANIZATION_ROUNDUP, quarter.key())
                .orElseGet(() -> renderLive(organizationId, organizationName, viewingUserId, quarter));
    }

    private RenderedMessage renderLive(String organizationId, String organizationName, String viewingUserId,
            Quarter quarter) {

        var facts = RoundupFacts.of(assembler.read(organizationId, organizationName, quarter));
        return renderPort.render(RenderRequest.builder().organizationId(organizationId).userId(viewingUserId)
                .type(MessageType.ORGANIZATION_ROUNDUP).template(TEMPLATE).facts(facts).build());
    }
}
