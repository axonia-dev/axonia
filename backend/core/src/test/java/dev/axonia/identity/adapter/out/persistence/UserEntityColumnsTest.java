package dev.axonia.identity.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every hand-written query that returns a member names its columns through {@link UserEntity#COLUMNS}. A query that
 * selects everything breaks on the release still serving while a migration drops a column, so the list is explicit, and
 * this keeps it equal to what the entity maps.
 */
class UserEntityColumnsTest {

    @Test
    void theSelectListNamesExactlyTheColumnsTheEntityMaps() {

        // given
        var mapped = new ArrayList<String>();
        new JdbcMappingContext().getRequiredPersistentEntity(UserEntity.class)
                .doWithProperties((PropertyHandler<RelationalPersistentProperty>) property -> mapped
                        .add(property.getColumnName().getReference()));

        // when
        var actual = Arrays.asList(UserEntity.COLUMNS.split(",\\s*"));

        // then
        assertThat(actual).containsExactlyInAnyOrderElementsOf(mapped);
    }
}
