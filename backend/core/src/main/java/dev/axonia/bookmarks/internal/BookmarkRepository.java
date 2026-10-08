package dev.axonia.bookmarks.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import dev.axonia.bookmarks.SaveableType;

public interface BookmarkRepository extends CrudRepository<BookmarkEntity, Long> {

    Optional<BookmarkEntity> findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId(String organizationId,
            String memberUserId, SaveableType saveableType, Long saveableId);

    List<BookmarkEntity> findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc(String organizationId,
            String memberUserId);
}
