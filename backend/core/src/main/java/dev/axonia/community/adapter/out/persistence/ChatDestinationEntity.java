package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.axonia.shared.api.persistence.Entity;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One place a community's session posts go. The webhook URL is a bearer token for a room: it never leaves this module
 * unmasked, and {@code toString} must never print it, which is why it is excluded from {@link ToString} rather than
 * trusted not to be logged.
 */
@Getter
@Builder(toBuilder = true)
@ToString(exclude = "webhookUrl")
@Table("community_chat_destination")
public class ChatDestinationEntity implements Entity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String name;
    private String webhookUrl;
    private boolean broken;
    private int consecutiveFailures;
    private Instant lastPostedAt;
    private Instant lastFailureAt;
    private String lastError;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
}
