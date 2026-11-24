package dev.axonia.mentoring.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.domain.ListingChange;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorListing;

@Repository
public class MentorListingRepositoryImpl implements MentorListingRepository {

    private static final String IN_SQL = """
            SELECT community_id, user_id, state, asked_at, told_at
              FROM mentoring_mentor_listing
             WHERE organization_id = :org AND community_id = :communityId
            """;

    private static final String INSERT = """
            INSERT INTO mentoring_mentor_listing (organization_id, community_id, user_id, state, asked_at, told_at,
                                                  updated_at)
            """;

    private static final String SET_SWITCH_SQL = INSERT + """
            VALUES (:org, :communityId, :userId, :state, NULL, NULL, :at)
            ON CONFLICT (organization_id, community_id, user_id) DO UPDATE
               SET state = EXCLUDED.state, updated_at = EXCLUDED.updated_at
            """;

    private static final String MARK_ASKED_SQL = INSERT + """
            VALUES (:org, :communityId, :userId, NULL, :at, NULL, :at)
            ON CONFLICT (organization_id, community_id, user_id) DO UPDATE
               SET asked_at = COALESCE(mentoring_mentor_listing.asked_at, EXCLUDED.asked_at),
                   updated_at = EXCLUDED.updated_at
            """;

    private static final String MARK_TOLD_SQL = INSERT + """
            VALUES (:org, :communityId, :userId, NULL, NULL, :at, :at)
            ON CONFLICT (organization_id, community_id, user_id) DO UPDATE
               SET told_at = COALESCE(mentoring_mentor_listing.told_at, EXCLUDED.told_at),
                   updated_at = EXCLUDED.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    MentorListingRepositoryImpl(NamedParameterJdbcTemplate jdbc) {

        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, MentorListing> in(String org, long communityId) {

        var listings = new HashMap<String, MentorListing>();
        jdbc.query(IN_SQL, new MapSqlParameterSource("org", org).addValue("communityId", communityId), rs -> {
            var state = rs.getString("state");
            var listing = new MentorListing(rs.getLong("community_id"), rs.getString("user_id"),
                    state == null ? ListingSwitch.DEFAULT : ListingSwitch.valueOf(state), Rows.instant(rs, "asked_at"),
                    Rows.instant(rs, "told_at"));
            listings.put(listing.userId(), listing);
        });
        return listings;
    }

    @Override
    @Transactional
    public void setSwitch(String org, ListingChange change) {

        jdbc.update(SET_SWITCH_SQL, key(org, change.communityId(), change.userId(), change.at()).addValue("state",
                change.state() == ListingSwitch.DEFAULT ? null : change.state().name()));
    }

    @Override
    @Transactional
    public void markAsked(String org, long communityId, String userId, Instant at) {

        jdbc.update(MARK_ASKED_SQL, key(org, communityId, userId, at));
    }

    @Override
    @Transactional
    public void markTold(String org, long communityId, String userId, Instant at) {

        jdbc.update(MARK_TOLD_SQL, key(org, communityId, userId, at));
    }

    private static MapSqlParameterSource key(String org, long communityId, String userId, Instant at) {

        return new MapSqlParameterSource("org", org).addValue("communityId", communityId).addValue("userId", userId)
                .addValue("at", Rows.timestamp(at));
    }
}
