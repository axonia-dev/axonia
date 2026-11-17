package dev.axonia.identity.adapter.out.persistence;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.identity.domain.ReadName;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class UserNameRepositoryImpl implements UserNameRepository {

    private static final String UPDATE = """
            UPDATE identity_user
            SET received_name = coalesce(received_name, display_name), display_name = :displayName,
                given_name = :givenName, version = version + 1
            WHERE organization_id = :organizationId AND id = :id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public void updateNames(String organizationId, Map<Long, ReadName> names) {

        var rows = names.entrySet().stream()
                .map(entry -> new MapSqlParameterSource().addValue("organizationId", organizationId)
                        .addValue("id", entry.getKey()).addValue("displayName", entry.getValue().displayName())
                        .addValue("givenName", entry.getValue().givenName()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(UPDATE, rows);
    }
}
