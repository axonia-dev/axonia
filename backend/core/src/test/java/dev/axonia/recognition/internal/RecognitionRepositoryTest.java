package dev.axonia.recognition.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.ContributionType;
import dev.axonia.recognition.mother.LoveEntityMother;
import dev.axonia.recognition.mother.RecognizedContributionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecognitionRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private RecognizedContributionRepository recognizedContributionRepository;

    @Autowired
    private LoveRepository loveRepository;

    @Test
    void findByContributionRef_isTenantScoped() {

        // given
        var saved = recognizedContributionRepository.save(RecognizedContributionEntityMother.any().build());

        // when
        var actual = recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId(
                "default", saved.getContributionType(), saved.getContributionId());

        // then
        assertThat(actual).isPresent();
        assertThat(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionId("acme",
                saved.getContributionType(), saved.getContributionId())).isEmpty();
    }

    @Test
    void recognizedContribution_uniquePerContributionRef() {

        // given
        recognizedContributionRepository.save(RecognizedContributionEntityMother.any().build());

        // when / then
        assertThatThrownBy(
                () -> recognizedContributionRepository.save(RecognizedContributionEntityMother.any().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void love_uniquePerMemberPerContribution() {

        // given
        var rc = recognizedContributionRepository.save(RecognizedContributionEntityMother.any().build());
        loveRepository.save(LoveEntityMother.any().recognizedContributionId(rc.getId()).build());

        // when / then
        assertThatThrownBy(
                () -> loveRepository.save(LoveEntityMother.any().recognizedContributionId(rc.getId()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRecognizedContributionCascadesLoves() {

        // given
        var rc = recognizedContributionRepository.save(RecognizedContributionEntityMother.any().build());
        loveRepository.save(LoveEntityMother.any().recognizedContributionId(rc.getId()).build());

        // when
        recognizedContributionRepository.delete(rc);

        // then
        assertThat(loveRepository.findByOrganizationIdAndRecognizedContributionId("default", rc.getId())).isEmpty();
    }

    @Test
    void findByContributionTypeAndContributionIdIn_isTenantScopedAndFiltersByType() {

        // given
        var slot = recognizedContributionRepository.save(RecognizedContributionEntityMother.any().contributionId(1L)
                .occurredAt(Instant.parse("2026-01-01T00:00:00Z")).build());
        recognizedContributionRepository
                .save(RecognizedContributionEntityMother.any().contributionType(ContributionType.ARTIFACT_CAPTURED)
                        .contributionId(2L).occurredAt(Instant.parse("2026-05-01T00:00:00Z")).build());

        // when
        var actual = recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn(
                "default", ContributionType.SLOT_PRESENTED, List.of(1L, 2L));

        // then
        assertThat(actual).extracting(RecognizedContributionEntity::getId).containsExactly(slot.getId());
        assertThat(recognizedContributionRepository.findByOrganizationIdAndContributionTypeAndContributionIdIn("acme",
                ContributionType.SLOT_PRESENTED, List.of(1L))).isEmpty();
    }
}
