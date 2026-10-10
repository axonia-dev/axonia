package dev.axonia.events.api.spi;

import dev.axonia.events.api.SessionResources;

/**
 * Narrow read port a wrap-up composer uses to learn which resources and slides were attached to a session - both to its
 * talks and to the session itself - without {@code events} ever depending on {@code knowledge} - the module that owns
 * artifacts and attachments. Declared here, in the module that needs the answer, and implemented by {@code knowledge},
 * the same dependency inversion {@code communication.api.spi.MessageSubjectPolicy} uses (implemented in
 * {@code events.adapter.out.communication}) to keep the module graph acyclic. {@code knowledge} already depends on
 * {@code events} (its own {@code SessionAccess} port is implemented by {@code knowledge.adapter.out.events} reaching
 * into {@code events.api}), so an adapter there that implements this port adds no new edge; declaring the port the
 * other way round - {@code events} importing a {@code knowledge} read port - would create the cycle
 * {@code ApplicationModules.verify()} rejects.
 */
public interface SessionResourceAccess {

    /**
     * The resources attached to {@code sessionId}: {@code sessionLevel} for those attached to the session itself -
     * {@code artifact_attachment.segment_id} left null - and {@code bySlot} for those attached to one of its talks,
     * keyed by slot id. A slot with no resources attached is simply absent from the map, never present with an empty
     * list - the caller decides what "none" means for its own purposes; the same holds for {@code sessionLevel} when
     * the session itself carries none.
     *
     * <p>
     * {@code communityId} is {@code sessionId}'s own community - the caller already knows it, since it is the caller
     * who read the session entity in the first place - and constrains the answer to resources belonging to that same
     * community. Without it, a resource attached under a session id that is org-visible by design but belongs to a
     * different community would render here regardless of which community the session itself belongs to.
     */
    SessionResources resourcesForSession(String organizationId, long sessionId, long communityId);
}
