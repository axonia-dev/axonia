package dev.axonia.community.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.AssertjAssertions;
import dev.axonia.AssertjComparators;
import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CommunityKindRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private CommunityKindRepository communityKindRepository;

    @Test
    void save() {

        // given
        var kind = CommunityKindEntityMother.any().label("Tribe").build();

        // when
        var actual = communityKindRepository.save(kind);

        // then
        AssertjAssertions.assertEntityAuditingOnSave(actual);
        assertThat(actual)
                .usingComparatorForType(AssertjComparators.temporalCloseTo(within(10, ChronoUnit.SECONDS)),
                        Instant.class)
                .usingRecursiveComparison().ignoringFields("id", "version", "createdAt", "updatedAt").isEqualTo(kind);
    }

    @Test
    void findByOrganizationIdAndId() {

        // given
        var saved = communityKindRepository.save(CommunityKindEntityMother.any().label("Squad").build());

        // when
        var actual = communityKindRepository.findByOrganizationIdAndId("default", saved.getId());

        // then
        assertThat(actual).isPresent().get().extracting(CommunityKindEntity::getLabel).isEqualTo("Squad");
    }

    @Test
    void findByOrganizationIdAndId_otherTenant() {

        // given
        var saved = communityKindRepository.save(CommunityKindEntityMother.any().label("Squad").build());

        // when - look up under a different organization that owns no such entity
        var actual = communityKindRepository.findByOrganizationIdAndId("other", saved.getId());

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void existsByOrganizationIdAndLabel() {

        // given
        communityKindRepository.save(CommunityKindEntityMother.any().label("Guild").build());

        // when
        var actual = communityKindRepository.existsByOrganizationIdAndLabel("default", "Guild");

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void save_duplicateLabelSameTenant_violatesUniqueConstraint() {

        // given
        communityKindRepository.save(CommunityKindEntityMother.any().label("Guild").build());

        // when / then
        assertThatThrownBy(() -> communityKindRepository.save(CommunityKindEntityMother.any().label("Guild").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
