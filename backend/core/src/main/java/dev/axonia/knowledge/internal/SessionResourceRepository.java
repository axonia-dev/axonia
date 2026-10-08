package dev.axonia.knowledge.internal;

import java.util.List;

public interface SessionResourceRepository {

    /**
     * The resources attached to {@code sessionId} - both direct attachments and provenance-only orphans - constrained
     * to artifacts belonging to {@code communityId}. Both callers ({@code ResourceService.resourcesForSession} and
     * {@code SessionResourceAccessAdapter}) already know the session's owning community before calling this, so the
     * parameter is required rather than optional: a row planted by naming a session outside the caller's community can
     * no longer be written (see {@code SessionProvenanceGuard}), but a row planted before that guard existed still sits
     * in the table, and this is what keeps such a row from being served to a community that never should have seen it.
     */
    List<SessionResourceRow> findForSession(String organizationId, Long sessionId, Long communityId);
}
