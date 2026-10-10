package dev.axonia.bookmarks.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.BookmarkMother;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SaveableType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcBookmarkStoreTest {

    private static final Bookmark STORED = BookmarkMother.anyArtifact().id(7L).helped(true)
            .helpedAt(Instant.parse("2026-06-03T00:00:00Z")).updatedAt(Instant.parse("2026-06-04T00:00:00Z"))
            .version(2L).build();

    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Mock
    private Clock clock;

    @InjectMocks
    private JdbcBookmarkStore sut;

    @Test
    void find_looks_up_the_members_save_of_the_item_and_maps_every_column() {

        // given
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdAndSaveableTypeAndSaveableId("default", "bob",
                SaveableType.ARTIFACT, 100L)).thenReturn(Optional.of(JdbcBookmarkStore.toEntity(STORED)));

        // when
        var actual = sut.find("default", "bob", SaveableRef.artifact(100L));

        // then
        assertThat(actual).contains(STORED);
    }

    @Test
    void listNewestFirst_keeps_the_repository_order() {

        // given
        var newer = BookmarkMother.anySession().id(2L).build();
        var older = BookmarkMother.anyArtifact().id(1L).build();
        when(bookmarkRepository.findByOrganizationIdAndMemberUserIdOrderByCreatedAtDesc("default", "bob"))
                .thenReturn(List.of(JdbcBookmarkStore.toEntity(newer), JdbcBookmarkStore.toEntity(older)));

        // when
        var actual = sut.listNewestFirst("default", "bob");

        // then
        assertThat(actual).containsExactly(newer, older);
    }

    @Test
    void add_inserts_the_new_save_and_leaves_an_existing_one_alone() {

        // given
        when(clock.instant()).thenReturn(NOW);

        // when
        sut.add(STORED);

        // then
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertThat(sql.getValue()).contains("ON CONFLICT (organization_id, member_user_id, saveable_type, saveable_id)")
                .contains("DO NOTHING");
        assertThat(params.getValue().getValues()).containsEntry("org", STORED.organizationId())
                .containsEntry("communityId", STORED.communityId())
                .containsEntry("saveableType", STORED.saveableType().name())
                .containsEntry("saveableId", STORED.saveableId()).containsEntry("memberUserId", STORED.memberUserId())
                .containsEntry("sourceAuthorUserId", STORED.sourceAuthorUserId())
                .containsEntry("now", Timestamp.from(NOW));
    }

    @Test
    void update_saves_every_column_back() {

        // when
        sut.update(STORED);

        // then
        var captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(JdbcBookmarkStore.toEntity(STORED));
    }

    @Test
    void remove_deletes_the_entity_with_its_id_and_version() {

        // when
        sut.remove(STORED);

        // then
        var captor = ArgumentCaptor.forClass(BookmarkEntity.class);
        verify(bookmarkRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getVersion()).isEqualTo(2L);
    }
}
