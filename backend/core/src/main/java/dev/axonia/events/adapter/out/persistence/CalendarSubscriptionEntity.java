package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * One member's calendar subscription to one community. {@code tokenHash} is all that is kept of the bearer the feed URL
 * carries, which is why it is excluded from {@code toString}: the token itself is handed over once, at mint or at
 * reset, and never again.
 *
 * <p>
 * There is no {@code updatedAt}, so this deliberately does not implement {@code Entity}. The only thing that changes
 * after creation is the fetch stamp, and {@code lastFetchedAt} already says when that happened, from the application's
 * own {@code Clock} rather than from SQL {@code now()}.
 */
@Getter
@Builder(toBuilder = true)
@ToString(exclude = "tokenHash")
@Table("calendar_subscription")
public class CalendarSubscriptionEntity {

    @Id
    private Long id;

    private String organizationId;
    private Long communityId;
    private String userId;
    private String tokenHash;

    @CreatedDate
    private Instant createdAt;

    private Instant lastFetchedAt;
    private String lastFetchedAgent;

    @Version
    private Long version;
}
