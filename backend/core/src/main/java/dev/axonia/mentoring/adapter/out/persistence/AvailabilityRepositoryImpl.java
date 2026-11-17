package dev.axonia.mentoring.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.domain.Availability;

@Repository
public class AvailabilityRepositoryImpl implements AvailabilityRepository {

    private static final String COLUMNS = """
            a.community_id, a.user_id, a.taking_mentees, a.mentee_limit, a.looking_for_mentor, a.updated_at
            """;

    private static final String FIND_SQL = "SELECT " + COLUMNS + """
              FROM mentoring_availability a
             WHERE a.organization_id = :org AND a.community_id = :communityId AND a.user_id = :userId
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO mentoring_availability (organization_id, community_id, user_id, taking_mentees, mentee_limit,
                                                looking_for_mentor, updated_at)
            VALUES (:org, :communityId, :userId, :takingMentees, :menteeLimit, :lookingForMentor, :updatedAt)
            ON CONFLICT (organization_id, community_id, user_id) DO UPDATE
               SET taking_mentees = EXCLUDED.taking_mentees,
                   mentee_limit = EXCLUDED.mentee_limit,
                   looking_for_mentor = EXCLUDED.looking_for_mentor,
                   updated_at = EXCLUDED.updated_at
            """;

    private static final String LISTED_FROM = "SELECT " + COLUMNS + """
              FROM mentoring_availability a
              JOIN identity_user u
                     ON u.organization_id = a.organization_id
                    AND u.user_id         = a.user_id
                    AND u.active          = true
              JOIN memberships m
                     ON m.organization_id = a.organization_id
                    AND m.community_id    = a.community_id
                    AND m.user_id         = a.user_id
             WHERE a.organization_id = :org AND a.community_id = :communityId
               AND (a.taking_mentees OR a.looking_for_mentor)
            """;

    private static final String LISTED_IN_SQL = LISTED_FROM + " ORDER BY a.updated_at, a.user_id";

    private static final String LISTED_SQL = LISTED_FROM + " AND a.user_id = :userId";

    private static final String DELETE_SQL = """
            DELETE FROM mentoring_availability
             WHERE organization_id = :org AND community_id = :communityId AND user_id = :userId
            """;

    private static final RowMapper<Availability> MAPPER = (rs, i) -> Availability.builder()
            .communityId(rs.getLong("community_id")).userId(rs.getString("user_id"))
            .takingMentees(rs.getBoolean("taking_mentees")).menteeLimit(rs.getObject("mentee_limit", Integer.class))
            .lookingForMentor(rs.getBoolean("looking_for_mentor")).updatedAt(Rows.instant(rs, "updated_at")).build();

    private final NamedParameterJdbcTemplate jdbc;

    AvailabilityRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Availability> find(String org, long communityId, String userId) {

        return jdbc.query(FIND_SQL, key(org, communityId, userId), MAPPER).stream().findFirst();
    }

    @Override
    @Transactional
    public void upsert(String org, Availability availability) {

        jdbc.update(UPSERT_SQL,
                key(org, availability.communityId(), availability.userId())
                        .addValue("takingMentees", availability.takingMentees())
                        .addValue("menteeLimit", availability.menteeLimit())
                        .addValue("lookingForMentor", availability.lookingForMentor())
                        .addValue("updatedAt", Rows.timestamp(availability.updatedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Availability> listedIn(String org, long communityId) {

        return jdbc.query(LISTED_IN_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId),
                MAPPER);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Availability> findListed(String org, long communityId, String userId) {

        return jdbc.query(LISTED_SQL, key(org, communityId, userId), MAPPER).stream().findFirst();
    }

    @Override
    @Transactional
    public void deleteIn(String org, long communityId, String userId) {

        jdbc.update(DELETE_SQL, key(org, communityId, userId));
    }

    private static MapSqlParameterSource key(String org, long communityId, String userId) {

        return new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("userId", userId);
    }
}
