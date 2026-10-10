package dev.axonia.communication.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private CommunicationDataPurger purger;

    @Test
    void purgeEmptiesEveryTable() {

        jdbc.update("INSERT INTO communication_preference (organization_id, user_id, message_type, enabled, "
                + "created_at, updated_at, version) VALUES (:org, 'member-a', 'session_upcoming', true, now(), "
                + "now(), 0)", Map.of("org", ORG));
        var messageId = jdbc.queryForObject(
                "INSERT INTO communication_message (organization_id, message_type, subject_ref, template, "
                        + "facts, created_at, updated_at, version) VALUES (:org, 'session_wrap_up', "
                        + "'session:1', 'session-template', '{}'::jsonb, now(), now(), 0) RETURNING id",
                Map.of("org", ORG), Long.class);
        jdbc.update("INSERT INTO communication_delivery (organization_id, message_id, user_id, status, "
                + "created_at, updated_at, version) VALUES (:org, :message, 'member-b', 'PENDING', "
                + "now(), now(), 0)", Map.of("org", ORG, "message", messageId));
        jdbc.update(
                "INSERT INTO communication_production_watermark (organization_id, producer_key, "
                        + "produced_through, updated_at, version) VALUES (:org, 'session-wrap-up', now(), now(), 0)",
                Map.of("org", ORG));
        jdbc.update("INSERT INTO communication_expired_message (organization_id, message_type, subject_ref, "
                + "pending_count, sent_count, suppressed_count, unreachable_count, abandoned_count, expired_at) "
                + "VALUES (:org, 'session_wrap_up', 'session:2', 0, 1, 0, 0, 0, now())", Map.of("org", ORG));

        purger.purge(ORG);

        assertThat(countPreference()).isZero();
        assertThat(countDelivery()).isZero();
        assertThat(countMessage()).isZero();
        assertThat(countWatermark()).isZero();
        assertThat(countExpiredMessage()).isZero();
    }

    private long countPreference() {

        return jdbc.queryForObject("SELECT count(*) FROM communication_preference WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private long countDelivery() {

        return jdbc.queryForObject("SELECT count(*) FROM communication_delivery WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private long countMessage() {

        return jdbc.queryForObject("SELECT count(*) FROM communication_message WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private long countWatermark() {

        return jdbc.queryForObject(
                "SELECT count(*) FROM communication_production_watermark WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private long countExpiredMessage() {

        return jdbc.queryForObject("SELECT count(*) FROM communication_expired_message WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }
}
