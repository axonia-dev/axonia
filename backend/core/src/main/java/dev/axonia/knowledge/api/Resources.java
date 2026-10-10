package dev.axonia.knowledge.api;

import java.util.List;

/** The artifacts attached to a session, and to its talks. */
public interface Resources {

    List<SessionResource> resourcesForSession(String slug, Long sessionId);

    Attachment attach(String slug, Long sessionId, AttachResourceRequest request);

    void detach(String slug, Long sessionId, Long artifactId, Long segmentId);
}
