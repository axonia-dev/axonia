package dev.axonia.knowledge.adapter.out.persistence;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactTypeRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private ArtifactTypeRepository artifactTypeRepository;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Test
    void existsByOrganizationIdAndLabel() {

        // given
        artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Recording").build());

        // when / then
        assertThat(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "Recording")).isTrue();
        assertThat(artifactTypeRepository.existsByOrganizationIdAndLabel("default", "Whitepaper")).isFalse();
    }

    @Test
    void save_duplicateLabelSameTenant_violatesUniqueConstraint() {

        // given
        artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Recording").build());

        // when / then
        assertThatThrownBy(() -> artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Recording").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOrganizationIdAndIdIn() {

        // given
        var recording = artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Recording").build());
        var slides = artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Slides").build());
        artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Unrelated").build());

        // when
        var actual = artifactTypeRepository.findByOrganizationIdAndIdIn("default",
                List.of(recording.getId(), slides.getId()));

        // then
        assertThat(actual).extracting(ArtifactTypeEntity::getLabel).containsExactlyInAnyOrder("Recording", "Slides");
    }

    /** An empty {@code IN ()} is a syntax error in Postgres, so an empty id collection must be answered without one. */
    @Test
    void findByOrganizationIdAndIdIn_emptyIdsReturnsEmptyWithoutQuerying() {

        assertThat(artifactTypeRepository.findByOrganizationIdAndIdIn("default", List.of())).isEmpty();
    }

    @Test
    void countArtifactsByType_groupsByType() {

        // given two kinds of types and artifacts pointing at them
        var typeA = artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Type A").build()).getId();
        var typeB = artifactTypeRepository.save(ArtifactTypeEntityMother.any().label("Type B").build()).getId();
        artifactRepository.save(ArtifactEntityMother.any().typeId(typeA).url("https://example.com/a1").build());
        artifactRepository.save(ArtifactEntityMother.any().typeId(typeA).url("https://example.com/a2").build());
        artifactRepository.save(ArtifactEntityMother.any().typeId(typeB).url("https://example.com/b1").build());

        // when
        var actual = artifactTypeRepository.countArtifactsByType("default");

        // then
        assertThat(actual).filteredOn(row -> row.typeId().equals(typeA)).singleElement()
                .extracting(ArtifactTypeRepository.TypeArtifactCount::artifactCount).isEqualTo(2L);
        assertThat(actual).filteredOn(row -> row.typeId().equals(typeB)).singleElement()
                .extracting(ArtifactTypeRepository.TypeArtifactCount::artifactCount).isEqualTo(1L);
    }
}
