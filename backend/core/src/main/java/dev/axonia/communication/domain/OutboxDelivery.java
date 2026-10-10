package dev.axonia.communication.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One {@code communication_delivery} row flattened together with the {@link OutboxMessage} columns it is claimed
 * alongside, as read back exclusively by {@code JdbcOutbox}'s own hand-written {@code RowMapper} - never through a
 * Spring Data JDBC {@code CrudRepository}. There is deliberately no such repository for these tables: {@code enqueue}
 * needs {@code ON CONFLICT DO NOTHING} and {@code claim} needs {@code FOR UPDATE SKIP LOCKED}, neither expressible as a
 * derived or {@code @Query} method, so every read and write in this module goes through {@code JdbcOutbox}'s
 * {@code NamedParameterJdbcTemplate} calls instead. {@code messageType}, {@code subjectRef}, {@code template} and
 * {@code facts} come from the joined message, not the delivery row, which is why every consumer downstream of
 * {@code claim} kept working unchanged when the two tables were split apart.
 *
 * <p>
 * This type therefore carries none of {@code @Id}, {@code @Table}, {@code @CreatedDate}, {@code @LastModifiedDate} or
 * {@code @Version}: none of them would be honoured by anything. {@code created_at}, {@code updated_at} and
 * {@code version} are stamped by {@code JdbcOutbox.enqueue} itself, the same way every other hand-written insert in
 * this codebase stamps its own timestamps. Advertising a persistence contract this type does not have was the earlier
 * mistake here - along with a now-removed {@code @Transient facts} field, which existed only to placate a
 * {@code CrudRepository} that has since been deleted for the same reason.
 */
@Getter
@Builder
@ToString(exclude = "facts")
public class OutboxDelivery {

    private Long id;
    private Long messageId;
    private String organizationId;
    private String messageType;
    private String subjectRef;
    private String userId;
    private String channel;
    private Long chatDestinationId;
    private String template;
    private String facts;
    private String status;
    private int attempts;
    private Instant claimedAt;
    private Instant sentAt;
    private String suppressedReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
