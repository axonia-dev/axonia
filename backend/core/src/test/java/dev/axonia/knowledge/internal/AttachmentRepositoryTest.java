package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.knowledge.mother.ArtifactEntityMother;
import dev.axonia.knowledge.mother.AttachmentEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentRepositoryTest extends CoreJdbcTestSupport {

    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private ArtifactRepository artifactRepository;

    private Long artifactId() {

        return artifactRepository.save(ArtifactEntityMother.any().build()).getId();
    }

    @Test
    void savesAndFindsBySessionIdentity() {

        // given
        var aId = artifactId();

        // when
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // then
        assertThat(attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId("default", aId, 100L,
                7L)).isPresent();
    }

    @Test
    void findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId_isTenantScoped() {

        // given
        var aId = artifactId();
        attachmentRepository.save(AttachmentEntityMother.any().organizationId("acme").artifactId(aId).sessionId(100L)
                .segmentId(7L).build());

        // then
        assertThat(attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId("default", aId, 100L,
                7L)).isEmpty();
        assertThat(
                attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId("acme", aId, 100L, 7L))
                .isPresent();
    }

    @Test
    void uniqueConstraint_rejectsDuplicateLink() {

        // given
        var aId = artifactId();
        attachmentRepository.save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build());

        // then
        assertThatThrownBy(() -> attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(aId).sessionId(100L).segmentId(7L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingArtifactCascadesAttachment() {

        // given
        var artifact = artifactRepository.save(ArtifactEntityMother.any().build());
        var attachment = attachmentRepository.save(AttachmentEntityMother.any().artifactId(artifact.getId()).build());

        // when
        artifactRepository.delete(artifact);

        // then
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
    }
}
