package dev.axonia.mentoring.adapter.out.privacy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringDataPurgerTest extends PurgerTestSupport {

    private static final List<String> TABLES = List.of("mentoring_availability", "mentoring_proposal",
            "mentoring_pairing", "mentoring_topic", "mentoring_note", "mentoring_note_topic", "mentoring_pointer",
            "mentoring_words");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private MentoringDataPurger purger;

    @Test
    void purgingAnOrganizationDeletesEveryMentoringRowAndNothingOfAnother() {

        // given
        var purged = seedEveryTable("mentoring-purged-org");
        var kept = seedEveryTable("mentoring-kept-org");

        // when
        purger.purge("mentoring-purged-org");

        // then
        assertThat(TABLES).allSatisfy(table -> assertThat(purged.count(table)).as(table).isZero());
        assertThat(TABLES).allSatisfy(table -> assertThat(kept.count(table)).as(table).isEqualTo(1));
    }

    private MentoringRows seedEveryTable(String org) {

        var rows = new MentoringRows(jdbc, org);
        rows.availability(1L, "noah");
        rows.proposal(1L, "mia", "noah", "Hello");
        var pairing = rows.pairing(1L, "mia", "noah");
        var topic = rows.topic(pairing, "Kafka");
        rows.noteTopic(rows.note(pairing, "noah", "A note"), topic);
        rows.memberOnTopic(pairing, topic, "leo");
        rows.words(pairing, "Thanks");
        return rows;
    }
}
