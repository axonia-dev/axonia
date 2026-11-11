package dev.axonia.learning.application.port.out;

import java.util.List;
import java.util.Map;

import dev.axonia.learning.api.OpenFace;

/** Who is open to questions in the community, which the expertise context decides. */
public interface OpenPeople {

    /**
     * Per canonical area, in the order the expertise context lists them. An area missing from it has nobody open. Reads
     * the caller's organization from the request.
     */
    Map<String, List<OpenFace>> byArea(String slug);

    /**
     * Whether the member is open to questions on the area in the community right now, under the same rule
     * {@link #byArea} reads. Org-explicit, for the delivery gate, which asks off any request.
     */
    boolean isOpen(String organizationId, long communityId, String area, String userId);
}
