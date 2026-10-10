package dev.axonia.communication.adapter.out.persistence;

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

@Getter
@Builder(toBuilder = true)
@ToString
@Table("communication_preference")
public class NotificationPreferenceEntity implements Entity {

    @Id
    private Long id;
    private String organizationId;
    private String userId;
    private String messageType;
    private boolean enabled;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    @Version
    private Long version;
}
