package dev.axonia.bookmarks.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.bookmarks.application.port.out.BookmarkStore;
import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.SaveableRef;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcBookmarkStore implements BookmarkStore {

    private static final String ADD_SQL = """
            INSERT INTO bookmark (organization_id, community_id, saveable_type, saveable_id, member_user_id,
                                  source_author_user_id, created_at, updated_at, version)
            VALUES (:org, :communityId, :saveableType, :saveableId, :memberUserId, :sourceAuthorUserId, :now, :now, 0)
            ON CONFLICT (organization_id, member_user_id, saveable_type, saveable_id) DO NOTHING
            """;

    private final BookmarkRepository bookmarkRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<Bookmark> find(String organizationId, String memberUserId, SaveableRef ref) {
        return bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId(organizationId,
                memberUserId, ref.type(), ref.id()).map(JdbcBookmarkStore::toBookmark);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bookmark> listNewestFirst(String organizationId, String memberUserId) {
        return bookmarkRepository.findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc(organizationId, memberUserId)
                .stream().map(JdbcBookmarkStore::toBookmark).toList();
    }

    /**
     * A save of an item the member already saved is a no-op. {@code ON CONFLICT DO NOTHING} rather than catching the
     * unique violation: Postgres aborts the caller's transaction on the violation, so nothing could run after it.
     */
    @Override
    @Transactional
    public void add(Bookmark bookmark) {
        jdbc.update(ADD_SQL,
                new MapSqlParameterSource("org", bookmark.organizationId())
                        .addValue("communityId", bookmark.communityId())
                        .addValue("saveableType", bookmark.saveableType().name())
                        .addValue("saveableId", bookmark.saveableId()).addValue("memberUserId", bookmark.memberUserId())
                        .addValue("sourceAuthorUserId", bookmark.sourceAuthorUserId())
                        .addValue("now", Timestamp.from(clock.instant())));
    }

    @Override
    @Transactional
    public void update(Bookmark bookmark) {
        bookmarkRepository.save(toEntity(bookmark));
    }

    @Override
    @Transactional
    public void remove(Bookmark bookmark) {
        bookmarkRepository.delete(toEntity(bookmark));
    }

    static Bookmark toBookmark(BookmarkEntity entity) {
        return Bookmark.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .communityId(entity.getCommunityId()).saveableType(entity.getSaveableType())
                .saveableId(entity.getSaveableId()).memberUserId(entity.getMemberUserId())
                .sourceAuthorUserId(entity.getSourceAuthorUserId()).helped(entity.getHelped())
                .helpedAt(entity.getHelpedAt()).createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion()).build();
    }

    static BookmarkEntity toEntity(Bookmark bookmark) {
        return BookmarkEntity.builder().id(bookmark.id()).organizationId(bookmark.organizationId())
                .communityId(bookmark.communityId()).saveableType(bookmark.saveableType())
                .saveableId(bookmark.saveableId()).memberUserId(bookmark.memberUserId())
                .sourceAuthorUserId(bookmark.sourceAuthorUserId()).helped(bookmark.helped())
                .helpedAt(bookmark.helpedAt()).createdAt(bookmark.createdAt()).updatedAt(bookmark.updatedAt())
                .version(bookmark.version()).build();
    }
}
