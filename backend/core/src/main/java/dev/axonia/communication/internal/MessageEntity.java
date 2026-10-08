package dev.axonia.communication.internal;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * The message half of the outbox, shared by every recipient of one thing being reported. Read exclusively by
 * {@link JdbcOutbox}'s own row mappers, never through a Spring Data JDBC repository, for the reason
 * {@link OutboxEntity} gives: {@code enqueue} needs {@code ON CONFLICT DO NOTHING} and {@code claim} needs
 * {@code FOR UPDATE SKIP LOCKED}, and neither is expressible as a derived method.
 */
@Getter
@Builder
@ToString(exclude = "facts")
class MessageEntity {

    private Long id;
    private String organizationId;
    private String messageType;
    private String subjectRef;
    private String template;
    private String facts;
}
