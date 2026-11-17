package dev.axonia.mentoring.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.mentoring.domain.MentoringPerson;

public interface PersonDirectory {

    /** Total: an id the directory cannot name maps to a "Deleted member" with no avatar, never to null. */
    Map<String, MentoringPerson> resolve(Collection<String> userIds);

    /**
     * Org-explicit, for callers that run off any request: the given name a pattern read, else the display name;
     * "Deleted member" for an id the directory cannot name.
     */
    String shortName(String org, String userId);

    /** Org-explicit: whether the person is still an active member of the organization. */
    boolean isPresent(String org, String userId);
}
