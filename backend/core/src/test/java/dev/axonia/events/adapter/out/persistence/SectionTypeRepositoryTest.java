package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.AssertjAssertions;
import dev.axonia.AssertjComparators;
import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SectionTypeRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void save() {

        // given
        var sectionType = SectionTypeEntityMother.any().build();

        // when
        var actual = sectionTypeRepository.save(sectionType);

        // then
        AssertjAssertions.assertEntityAuditingOnSave(actual);

        assertThat(actual).usingComparatorForType(AssertjComparators.temporalCloseTo(within(10, ChronoUnit.SECONDS)),
                Instant.class).usingRecursiveAssertion().ignoringFields("id", "version").isEqualTo(sectionType);
    }

    @Test
    void findByOrganizationIdOrderByLabelAsc() {

        // given
        sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("acme").label("Round Table")
                .expectsPresenter(false).build());
        sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("acme").label("Deep Dive").build());

        // when
        var actual = sectionTypeRepository.findByOrganizationIdOrderByLabelAsc("acme");

        // then
        assertThat(actual).extracting(SectionTypeEntity::getLabel).containsExactly("Deep Dive", "Round Table");
    }

    @Test
    void findByOrganizationIdAndId() {

        // given
        var saved = sectionTypeRepository.save(SectionTypeEntityMother.any().build());

        // when
        var actual = sectionTypeRepository.findByOrganizationIdAndId("acme", saved.getId());

        // then
        assertThat(actual).isPresent();
    }

    @Test
    void findByOrganizationIdAndIdIn() {

        // given
        var talk = sectionTypeRepository
                .save(SectionTypeEntityMother.any().organizationId("acme").label("Talk").build());
        var workshop = sectionTypeRepository
                .save(SectionTypeEntityMother.any().organizationId("acme").label("Workshop").build());
        sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("acme").label("Unrelated").build());

        // when
        var actual = sectionTypeRepository.findByOrganizationIdAndIdIn("acme", List.of(talk.getId(), workshop.getId()));

        // then
        assertThat(actual).extracting(SectionTypeEntity::getLabel).containsExactlyInAnyOrder("Talk", "Workshop");
    }

    /** An empty {@code IN ()} is a syntax error in Postgres, so an empty id collection must be answered without one. */
    @Test
    void findByOrganizationIdAndIdIn_emptyIdsReturnsEmptyWithoutQuerying() {

        assertThat(sectionTypeRepository.findByOrganizationIdAndIdIn("acme", List.of())).isEmpty();
    }

    @Test
    void existsByOrganizationIdAndLabel() {

        // given
        sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("acme").label("Dojo").build());

        // when / then
        assertThat(sectionTypeRepository.existsByOrganizationIdAndLabel("acme", "Dojo")).isTrue();
        assertThat(sectionTypeRepository.existsByOrganizationIdAndLabel("acme", "Nope")).isFalse();
    }

    @Test
    void save_duplicateLabelRejected() {

        // given
        sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("acme").label("Talk").build());

        // when / then
        assertThatThrownBy(() -> sectionTypeRepository
                .save(SectionTypeEntityMother.any().organizationId("acme").label("Talk").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countAndReassignStructureAndSectionRefs() {

        // given - two section types for the "default" org (needed for FK to event_section_type)
        var typeA = sectionTypeRepository
                .save(SectionTypeEntityMother.any().organizationId("default").label("Ref Type A").build());
        var typeB = sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("default")
                .label("Ref Type B").expectsPresenter(false).build());

        // seed a community row required by the series/session FKs (mirror SeriesRepositoryTest)
        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'GuildRef', now(), now(), 0) ON CONFLICT DO NOTHING");
        var communityId = jdbc
                .queryForObject("INSERT INTO communities (organization_id, slug, name, kind_id, description, status, "
                        + "created_at, updated_at, version) "
                        + "SELECT 'default', 'ref-' || nextval('communities_id_seq'), 'Ref', k.id, '', 'ACTIVE', "
                        + "now(), now(), 0 FROM community_kind k "
                        + "WHERE k.organization_id = 'default' AND k.label = 'GuildRef' RETURNING id", Long.class);

        // seed a series with a default-structure ref to typeA
        var series = seriesRepository.save(SeriesEntityMother.any().communityId(communityId)
                .defaultStructure(List.of(SectionTypeRef.builder().sectionTypeId(typeA.getId()).build())).build());

        // seed a session with one section referencing typeA
        sessionRepository.save(SessionEntityMother.any().communityId(communityId).seriesId(series.getId())
                .scheduledAt(Instant.parse("2099-06-01T18:00:00Z")).occurrenceDate(LocalDate.of(2099, 6, 1))
                .sections(List.of(SessionEntityMother.section(typeA.getId()))).build());

        // when / then - initial counts
        assertThat(sectionTypeRepository.countStructureRefs(typeA.getId())).isEqualTo(1L);
        assertThat(sectionTypeRepository.countStructureRefs(typeB.getId())).isEqualTo(0L);
        assertThat(sectionTypeRepository.countSectionRefs(typeA.getId())).isEqualTo(1L);
        assertThat(sectionTypeRepository.countSectionRefs(typeB.getId())).isEqualTo(0L);

        // when - reassign both from A to B
        sectionTypeRepository.reassignStructureRefs(typeA.getId(), typeB.getId());
        sectionTypeRepository.reassignSectionRefs(typeA.getId(), typeB.getId());

        // then - refs moved
        assertThat(sectionTypeRepository.countStructureRefs(typeA.getId())).isEqualTo(0L);
        assertThat(sectionTypeRepository.countStructureRefs(typeB.getId())).isEqualTo(1L);
        assertThat(sectionTypeRepository.countSectionRefs(typeA.getId())).isEqualTo(0L);
        assertThat(sectionTypeRepository.countSectionRefs(typeB.getId())).isEqualTo(1L);
    }
}
