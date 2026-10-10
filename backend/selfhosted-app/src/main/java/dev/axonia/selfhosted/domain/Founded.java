package dev.axonia.selfhosted.domain;

import java.time.Instant;

/** The Organization the installation founded, the subject claim it matches accounts on, and when. */
public record Founded(String organizationId, String subjectClaim, Instant foundedAt) {
}
