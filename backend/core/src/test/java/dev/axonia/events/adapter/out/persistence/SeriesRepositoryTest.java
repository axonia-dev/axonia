package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.AssertjAssertions;
import dev.axonia.AssertjComparators;
import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeriesRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCommunities() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 1,'default','c1','C1', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT 2,'default','c2','C2', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING");
    }

    @Test
    void save() {

        // given
        var series = SeriesEntityMother.any().build();

        // when
        var actual = seriesRepository.save(series);

        // then
        AssertjAssertions.assertEntityAuditingOnSave(actual);
        assertThat(actual)
                .usingComparatorForType(AssertjComparators.temporalCloseTo(within(10, ChronoUnit.SECONDS)),
                        Instant.class)
                .usingRecursiveComparison().ignoringFields("id", "version", "createdAt", "updatedAt").isEqualTo(series);
    }

    @Test
    void findByOrganizationIdAndCommunityId() {

        // given
        seriesRepository.save(SeriesEntityMother.any().communityId(1L).build());
        seriesRepository.save(SeriesEntityMother.any().communityId(2L).build());

        // when
        var actual = seriesRepository.findByOrganizationIdAndCommunityId("default", 1L, PageRequest.of(0, 20));

        // then
        assertThat(actual.getContent()).singleElement().satisfies(e -> assertThat(e.getCommunityId()).isEqualTo(1L));
    }

    @Test
    void findByOrganizationIdAndId() {

        // given
        var saved = seriesRepository.save(SeriesEntityMother.any().build());

        // when
        var actual = seriesRepository.findByOrganizationIdAndId("default", saved.getId());

        // then
        assertThat(actual).isPresent().get().satisfies(e -> assertThat(e.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void findByOrganizationIdAndId_otherTenantIsIsolated() {

        // given
        var saved = seriesRepository.save(SeriesEntityMother.any().build());

        // when
        var actual = seriesRepository.findByOrganizationIdAndId("other-organization", saved.getId());

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void save_persistsOrderedDefaultStructure() {

        // given - a community row the FK needs, plus two section types to reference
        var typeA = sectionTypeRepository
                .save(SectionTypeEntityMother.any().organizationId("default").label("Lightning Talk X").build())
                .getId();
        var typeB = sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("default")
                .label("Round Table X").expectsPresenter(false).build()).getId();

        var series = SeriesEntityMother.any().communityId(1L)
                .defaultStructure(List.of(SectionTypeRef.builder().sectionTypeId(typeA).build(),
                        SectionTypeRef.builder().sectionTypeId(typeB).build()))
                .build();

        // when
        var saved = seriesRepository.save(series);
        var reloaded = seriesRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();

        // then - order preserved by the `position` key column
        assertThat(reloaded.getDefaultStructure()).extracting(SectionTypeRef::getSectionTypeId).containsExactly(typeA,
                typeB);
    }
}
