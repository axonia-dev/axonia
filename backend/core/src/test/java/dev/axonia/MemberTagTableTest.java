package dev.axonia;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberTagTableTest {

    private record Row(long communityId, String userId, String tag, Instant declaredAt) implements MemberTag {
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Learning_intent", "learning_intent t; DROP TABLE users", "intent2", "public.intent"})
    void refusesATableNameThatIsNotAPlainLowerCaseIdentifier(String table) {

        // given
        var jdbc = mock(NamedParameterJdbcTemplate.class);

        // when / then
        assertThatThrownBy(() -> new MemberTagTable<>(jdbc, table, Row::new))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(table);
    }

    @Test
    void splicesAPlainTableNameIntoItsStatements() {

        // given
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var sut = new MemberTagTable<>(jdbc, "learning_intent", Row::new);

        // when
        sut.tagsIn("acme", 7L);

        // then
        verify(jdbc).queryForList(contains("FROM learning_intent t"), any(SqlParameterSource.class), eq(String.class));
    }
}
