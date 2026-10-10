package dev.axonia.knowledge.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Builder;

/**
 * The act {@code ArtifactOpeningLog#record} carries out: a parameter record rather than a six-argument method, because
 * PMD refuses a method with five or more parameters and this repo's convention is a well-named record rather than a
 * suppression.
 *
 * <p>
 * Deliberately not an entity. {@code artifact_opening} has no {@code CrudRepository} and no JDBC-mapped read model: the
 * row is written only through {@code ArtifactOpeningWriter}'s raw SQL and read back only by
 * {@code OpeningSignalsAdapter} and the personal data export, both also raw SQL. This type exists solely to carry one
 * call's arguments across the method boundary and must not grow persistence concerns of its own.
 *
 * <p>
 * {@code createdAt} and {@code openedOn} both arrive from the service layer's Clock; nothing here or downstream reads
 * the database's clock.
 */
@Builder
public record RecordOpeningCommand(String organizationId, long communityId, long artifactId, String memberUserId,
        Instant createdAt, LocalDate openedOn) {
}
