package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import dev.axonia.AssertjAssertions;
import dev.axonia.AssertjComparators;
import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.community.api.CommunityStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CommunityRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private CommunityKindRepository communityKindRepository;

    private Long kindId;

    @BeforeEach
    void seedKind() {

        kindId = communityKindRepository.save(CommunityKindEntityMother.any().build()).getId();
    }

    @Test
    void save() {

        // given
        var community = CommunityEntityMother.any().kindId(kindId).build();

        // when
        var actual = communityRepository.save(community);

        // then
        AssertjAssertions.assertEntityAuditingOnSave(actual);

        assertThat(actual)
                .usingComparatorForType(AssertjComparators.temporalCloseTo(within(10, ChronoUnit.SECONDS)),
                        Instant.class)
                .usingRecursiveComparison().ignoringFields("id", "version", "createdAt", "updatedAt")
                .isEqualTo(community);
    }

    @Test
    void findByOrganizationIdAndSlug() {

        // given
        var saved = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when
        var actual = communityRepository.findByOrganizationIdAndSlug("default", "backend-guild");

        // then
        assertThat(actual).isPresent().get()
                .usingComparatorForType(AssertjComparators.temporalCloseTo(within(10, ChronoUnit.SECONDS)),
                        Instant.class)
                .usingRecursiveComparison().isEqualTo(saved);
    }

    @Test
    void findByOrganizationIdAndSlug_otherTenant() {

        // given
        communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when
        var actual = communityRepository.findByOrganizationIdAndSlug("other", "backend-guild");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void existsByOrganizationIdAndSlug() {

        // given
        communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when
        var actual = communityRepository.existsByOrganizationIdAndSlug("default", "backend-guild");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void save_duplicateSlugSameTenant_violatesUniqueConstraint() {

        // given
        communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when / then
        assertThatThrownBy(() -> communityRepository.save(CommunityEntityMother.any().kindId(kindId).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_sameSlugDifferentTenant_allowed() {

        // given
        communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when
        var actual = communityRepository
                .save(CommunityEntityMother.any().organizationId("other").kindId(kindId).build());

        // then
        assertThat(actual.getId()).isNotNull();
    }

    @Test
    void findByOrganizationIdAndStatusAndNameContainingIgnoreCase() {

        // given
        communityRepository
                .save(CommunityEntityMother.any().slug("frontend-guild").name("Frontend Guild").kindId(kindId).build());
        communityRepository
                .save(CommunityEntityMother.any().slug("design-circle").name("Design Circle").kindId(kindId).build());

        // when
        var actual = communityRepository.findByOrganizationIdAndStatusAndNameContainingIgnoreCase("default",
                CommunityStatus.ACTIVE, "guild", PageRequest.of(0, 10));

        // then
        assertThat(actual.getContent()).hasSize(1);
        assertThat(actual.getContent().getFirst().getName()).isEqualTo("Frontend Guild");
    }

    @Test
    void save_persistsContributionPolicyColumns() {

        // given
        var community = CommunityEntityMother.any().canStartSeries(true).canQueueTopics(true).kindId(kindId).build();

        // when
        var saved = communityRepository.save(community);

        // then
        var actual = communityRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();
        assertThat(actual.isCanStartSeries()).isTrue();
        assertThat(actual.isCanEditStructure()).isFalse();
        assertThat(actual.isCanQueueTopics()).isTrue();
    }

    @Test
    void save_persistsManagementPolicyColumns() {

        // given
        var community = CommunityEntityMother.any().canEditCommunity(true).canManageMembers(true).kindId(kindId)
                .build();

        // when
        var saved = communityRepository.save(community);

        // then
        var actual = communityRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();
        assertThat(actual.isCanEditCommunity()).isTrue();
        assertThat(actual.isCanManageLookAndFeel()).isFalse();
        assertThat(actual.isCanManageMembers()).isTrue();
        assertThat(actual.isCanManageContributionPolicy()).isFalse();
        assertThat(actual.isCanArchiveCommunity()).isFalse();
    }

    @Test
    void save_persistsTheContributionCreditingColumn() {

        // given
        var community = CommunityEntityMother.any().canCreditContributions(true).kindId(kindId).build();

        // when
        var saved = communityRepository.save(community);

        // then
        var actual = communityRepository.findByOrganizationIdAndId("default", saved.getId()).orElseThrow();
        assertThat(actual.isCanCreditContributions()).isTrue();
    }

    @Test
    void countCommunitiesByKind_groupsByKind() {

        // given two kinds and communities pointing at them
        var kindA = communityKindRepository.save(CommunityKindEntityMother.any().label("Kind A").build()).getId();
        var kindB = communityKindRepository.save(CommunityKindEntityMother.any().label("Kind B").build()).getId();
        communityRepository.save(CommunityEntityMother.any().slug("a-one").name("A One").kindId(kindA).build());
        communityRepository.save(CommunityEntityMother.any().slug("a-two").name("A Two").kindId(kindA).build());
        communityRepository.save(CommunityEntityMother.any().slug("b-one").name("B One").kindId(kindB).build());

        // when
        var actual = communityRepository.countCommunitiesByKind("default");

        // then
        assertThat(actual).filteredOn(row -> row.kindId().equals(kindA)).singleElement()
                .extracting(CommunityRepository.KindCommunityCount::communityCount).isEqualTo(2L);
        assertThat(actual).filteredOn(row -> row.kindId().equals(kindB)).singleElement()
                .extracting(CommunityRepository.KindCommunityCount::communityCount).isEqualTo(1L);
    }

    @Test
    void findByOrganizationIdAndId_otherTenant() {

        // given
        var saved = communityRepository.save(CommunityEntityMother.any().kindId(kindId).build());

        // when
        var actual = communityRepository.findByOrganizationIdAndId("other", saved.getId());

        // then
        assertThat(actual).isEmpty();
    }
}
