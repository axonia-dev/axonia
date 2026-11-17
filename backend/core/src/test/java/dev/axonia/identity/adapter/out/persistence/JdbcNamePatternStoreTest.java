package dev.axonia.identity.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.identity.domain.NamePatterns;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcNamePatternStoreTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final List<String> TWO = List.of("{family}, {given}-NORTHWIND", "{family}, {given}-EXTERNAL");

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private JdbcNamePatternStore store;

    @BeforeEach
    void assembleStore() {

        store = new JdbcNamePatternStore(jdbc);
    }

    @Test
    void anOrganizationThatDeclaredNothingHoldsNone() {

        assertThat(store.find(ORG)).isEqualTo(NamePatterns.NONE);
    }

    @Test
    void keepsTheOrderGiven() {

        // when
        store.replace(ORG, NamePatterns.of(TWO));

        // then
        assertThat(store.find(ORG).texts()).containsExactlyElementsOf(TWO);
    }

    @Test
    void replacingRewritesTheWholeList() {

        // given
        store.replace(ORG, NamePatterns.of(TWO));

        // when
        store.replace(ORG, NamePatterns.of(List.of("{given} {family}-EXTERNAL")));

        // then
        assertThat(store.find(ORG).texts()).containsExactly("{given} {family}-EXTERNAL");
    }

    @Test
    void replacingWithNoneEmptiesTheList() {

        // given
        store.replace(ORG, NamePatterns.of(TWO));

        // when
        store.replace(ORG, NamePatterns.NONE);

        // then
        assertThat(store.find(ORG)).isEqualTo(NamePatterns.NONE);
    }

    @Test
    void anotherOrganizationsPatternsStayItsOwn() {

        // when
        store.replace("acme", NamePatterns.of(TWO));

        // then
        assertThat(store.find(ORG)).isEqualTo(NamePatterns.NONE);
        assertThat(store.find("acme").texts()).containsExactlyElementsOf(TWO);
    }
}
