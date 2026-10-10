package dev.axonia.communication.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

/**
 * RLS already scopes every row to the current org, but the organization id is still passed explicitly on every query as
 * defence-in-depth (the same convention {@code expertise.adapter.out.persistence.PreferenceRepositoryImpl} and
 * {@code bookmarks.adapter.out.persistence.BookmarkRepository} follow): a connection with no {@code app.current_org}
 * set - an owner connection, a future sweep or purge job, a test - must not see or touch another organization's rows,
 * and one {@code user_id} can legitimately have a row in more than one organization.
 */
public interface NotificationPreferenceRepository extends CrudRepository<NotificationPreferenceEntity, Long> {

    List<NotificationPreferenceEntity> findByOrganizationIdAndUserId(String organizationId, String userId);

    Optional<NotificationPreferenceEntity> findByOrganizationIdAndUserIdAndMessageType(String organizationId,
            String userId, String messageType);
}
