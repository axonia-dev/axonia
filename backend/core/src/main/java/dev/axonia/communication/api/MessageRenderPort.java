package dev.axonia.communication.api;

import java.util.Optional;

/**
 * Renders a message for something other than delivery. {@code events} is the first and so far only caller: its release
 * preview needs the exact subject and HTML a real send would produce, before anyone commits to mailing a whole
 * community, so it renders through this port rather than through any path of its own. The port takes the message type
 * as an argument, which is what lets one preview endpoint serve every message an organizer controls. The implementation
 * calls the same {@code MailRenderer} the outbox drain calls and mints the same shape of unsubscribe footer link the
 * drain mints - the guarantee this port exists to make is that a preview cannot render differently from the message the
 * drain would eventually send for the same facts, because there is only the one rendering path for either to take.
 *
 * <p>
 * Rendering something never sends it. This port has no opinion on delivery, suppression or the outbox at all - see
 * {@link CommunicationPort#enqueue} for the one method that actually queues a message to go out.
 */
public interface MessageRenderPort {

    /** Renders {@code request.facts()} exactly as given - the caller is assembling them live, right now. */
    RenderedMessage render(RenderRequest request);

    /**
     * Renders the one already-enqueued outbox row for {@code (type, subjectRef)}, if one exists, from that row's own
     * stored facts rather than anything the caller could assemble live. Once a message has been enqueued, its facts are
     * frozen at the instant the sender enqueued it - the frozen row is "the message" from that instant on, in every
     * sense that matters to a recipient, whether it has been sent yet or not. A caller previewing after that point must
     * show the frozen version, or the preview can answer a question nobody asked ("what would this look like if it were
     * re-derived right now") while claiming to answer the one that matters ("what will - or did - the recipient see").
     * Empty when no row exists yet for this subject, which is the caller's signal to fall back to {@link #render} with
     * live facts instead - there is nothing frozen yet to be faithful to.
     *
     * <p>
     * The token is still minted for {@code previewingUserId}, never for whichever recipient the stored row happens to
     * belong to: every recipient's row carries identical facts (the sender fans one call out to many rows), but the
     * unsubscribe link must always be honest for the person looking at the preview, exactly as {@link #render} already
     * is.
     *
     * <p>
     * {@code organizationId} is never part of the lookup itself - the implementation reads through a connection scoped
     * by the {@code app.current_org} GUC, the same defence-in-depth convention every other outbox read in this module
     * follows, and only uses {@code organizationId} to mint the token above. That means empty MUST mean "no row is
     * enqueued for this subject yet" and nothing else: if a caller ever reached this method on a connection whose GUC
     * was not correctly scoped, the query would silently match zero rows and this method would answer empty exactly as
     * it does for the genuine no-row case, sending the caller down the live-facts fallback in {@link #render} instead
     * of failing loudly. A caller MUST NOT be reachable in a state where that scoping could be wrong - see
     * {@code SessionReleaseService#preview}'s own javadoc for how its one caller closes that window.
     */
    Optional<RenderedMessage> renderEnqueued(String organizationId, String previewingUserId, MessageType type,
            String subjectRef);
}
