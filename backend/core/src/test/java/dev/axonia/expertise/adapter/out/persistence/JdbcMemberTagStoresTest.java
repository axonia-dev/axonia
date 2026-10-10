package dev.axonia.expertise.adapter.out.persistence;

import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import dev.axonia.expertise.api.MemberTag;
import dev.axonia.expertise.api.MemberTagKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcMemberTagStoresTest {

    private record Row(long communityId, String userId, String tag, Instant declaredAt) implements MemberTag {
    }

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcMemberTagStores sut = new JdbcMemberTagStores(jdbc);

    @ParameterizedTest
    @EnumSource(MemberTagKind.class)
    void everyKindNamesItsOwnTable(MemberTagKind kind) {

        assertThat(JdbcMemberTagStores.tableOf(kind)).isEqualTo(kind.name().toLowerCase(Locale.ROOT));
    }

    @Test
    void opensAStoreOverTheKindsTable() {

        // given
        var store = sut.of(MemberTagKind.LEARNING_INTENT, Row::new);

        // when
        store.tagsIn("acme", 7L);

        // then
        assertThat(store).isInstanceOf(MemberTagTable.class);
        verify(jdbc).queryForList(contains("FROM learning_intent t"), any(SqlParameterSource.class), eq(String.class));
    }

    @Test
    void readsTheActiveRowsOfTheKindsTableThroughTheSharedRowMapping() {

        // given
        var store = sut.of(MemberTagKind.LEARNING_INTENT, Row::new);

        // when
        store.activeIn("acme", 7L);

        // then
        verify(jdbc).query(contains("FROM learning_intent t"),
                argThat((SqlParameterSource params) -> "acme".equals(params.getValue("org"))
                        && Long.valueOf(7L).equals(params.getValue("communityId"))),
                any(RowMapper.class));
    }
}
