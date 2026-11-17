package dev.axonia.identity.adapter.out.persistence;

import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.domain.NamePatterns;
import lombok.RequiredArgsConstructor;

/** One {@code identity_name_pattern} row per pattern; {@code position} keeps the declared order. */
@Component
@RequiredArgsConstructor
class JdbcNamePatternStore implements NamePatternStore {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public NamePatterns find(String organizationId) {

        var texts = jdbc.queryForList(
                "SELECT pattern FROM identity_name_pattern WHERE organization_id = :org ORDER BY position",
                Map.of("org", organizationId), String.class);
        return texts.isEmpty() ? NamePatterns.NONE : NamePatterns.of(texts);
    }

    @Override
    @Transactional
    public void replace(String organizationId, NamePatterns patterns) {

        jdbc.update("DELETE FROM identity_name_pattern WHERE organization_id = :org", Map.of("org", organizationId));
        var texts = patterns.texts();
        var rows = IntStream.range(0, texts.size())
                .mapToObj(position -> new MapSqlParameterSource().addValue("org", organizationId)
                        .addValue("position", position).addValue("pattern", texts.get(position)))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO identity_name_pattern (organization_id, position, pattern)"
                + " VALUES (:org, :position, :pattern)", rows);
    }
}
