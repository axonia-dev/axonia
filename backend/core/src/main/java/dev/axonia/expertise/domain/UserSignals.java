package dev.axonia.expertise.domain;

import java.util.List;

/**
 * One practitioner and the signals a practitioner or self-view query found for them.
 *
 * @param userId
 *            the practitioner's user identifier
 * @param signals
 *            all expertise signals contributing to that practitioner's profile (within the scope of the calling query)
 */
public record UserSignals(String userId, List<ExpertiseSignal> signals) {
}
