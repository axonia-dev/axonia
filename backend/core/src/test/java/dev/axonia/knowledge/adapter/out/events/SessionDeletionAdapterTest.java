package dev.axonia.knowledge.adapter.out.events;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntity;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntityMother;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.knowledge.adapter.out.persistence.AttachmentEntity;
import dev.axonia.knowledge.adapter.out.persistence.AttachmentEntityMother;
import dev.axonia.knowledge.adapter.out.persistence.AttachmentRepository;

import static org.assertj.core.api.Assertions.assertThat;

class SessionDeletionAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private SessionDeletionAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionDeletionAdapter(attachmentRepository, artifactRepository);
    }

    private ArtifactEntity artifact(String url, Long provenanceSessionId) {

        return artifactRepository.save(ArtifactEntityMother.any().url(url).searchDocument("x").tags(Set.of())
                .provenanceSessionId(provenanceSessionId).provenanceSegmentId(provenanceSessionId == null ? null : 7L)
                .provenanceLabel(provenanceSessionId == null ? null : "Talk A").build());
    }

    private void attach(Long artifactId, Long sessionId) {

        attachmentRepository
                .save(AttachmentEntityMother.any().artifactId(artifactId).sessionId(sessionId).segmentId(null).build());
    }

    @Test
    void sessionDeleted_dropsTheSessionsAttachmentsAndKeepsTheArtifacts() {

        // given
        var deck = artifact("https://example.com/deck.pdf", null);
        attach(deck.getId(), 100L);
        attach(deck.getId(), 200L);

        // when
        sut.sessionDeleted("default", 1L, 100L);

        // then
        assertThat(attachmentRepository.findAll()).extracting(AttachmentEntity::getSessionId).containsExactly(200L);
        assertThat(artifactRepository.findById(deck.getId())).isPresent();
    }

    @Test
    void sessionDeleted_forgetsTheProvenanceOfArtifactsCapturedFromIt() {

        // given
        var captured = artifact("https://example.com/captured", 100L);
        var other = artifact("https://example.com/other", 200L);

        // when
        sut.sessionDeleted("default", 1L, 100L);

        // then
        var actual = artifactRepository.findById(captured.getId()).orElseThrow();
        assertThat(actual.getProvenanceSessionId()).isNull();
        assertThat(actual.getProvenanceSegmentId()).isNull();
        assertThat(actual.getProvenanceLabel()).isNull();
        assertThat(artifactRepository.findById(other.getId()).orElseThrow().getProvenanceSessionId()).isEqualTo(200L);
    }
}
