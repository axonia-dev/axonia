package dev.axonia.knowledge.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.domain.StoredAttachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcAttachmentStoreTest {

    private static final Instant CREATED = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-03-02T10:00:00Z");

    @Mock
    private AttachmentRepository attachmentRepository;

    private JdbcAttachmentStore sut() {

        return new JdbcAttachmentStore(attachmentRepository);
    }

    private static AttachmentEntity fullEntity() {

        return AttachmentEntityMother.any().id(9L).createdAt(CREATED).updatedAt(UPDATED).version(1L).build();
    }

    @Test
    void find_mapsEveryColumn() {

        // given
        when(attachmentRepository.findByOrganizationIdAndArtifactIdAndSessionIdAndSegmentId("default", 1L, 100L, 7L))
                .thenReturn(Optional.of(fullEntity()));

        // when
        var actual = sut().find("default", 1L, 100L, 7L);

        // then
        assertThat(actual).contains(
                StoredAttachment.builder().id(9L).organizationId("default").artifactId(1L).sessionId(100L).segmentId(7L)
                        .attachedByUserId("dev-user").createdAt(CREATED).updatedAt(UPDATED).version(1L).build());
    }

    @Test
    void save_writesEveryColumnAndAnswersWhatWasStored() {

        // given
        var attachment = JdbcAttachmentStore.toAttachment(fullEntity());
        when(attachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut().save(attachment);

        // then
        var written = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(written.capture());
        assertThat(written.getValue()).usingRecursiveComparison().isEqualTo(fullEntity());
        assertThat(actual).isEqualTo(attachment);
    }

    @Test
    void delete_carriesTheIdAndVersion() {

        // when
        sut().delete(StoredAttachment.builder().id(9L).version(4L).build());

        // then
        var deleted = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).delete(deleted.capture());
        assertThat(deleted.getValue().getId()).isEqualTo(9L);
        assertThat(deleted.getValue().getVersion()).isEqualTo(4L);
    }
}
