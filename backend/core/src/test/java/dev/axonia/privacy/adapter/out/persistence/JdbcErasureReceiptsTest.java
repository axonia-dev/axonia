package dev.axonia.privacy.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.privacy.domain.ErasureReceipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@MockitoSettings
class JdbcErasureReceiptsTest {

    private static final Instant ERASED_AT = Instant.parse("2026-07-27T10:00:00Z");

    @Mock
    ErasureReceiptRepository repository;

    @Test
    void record_savesEveryFieldOfTheReceipt() {

        // given
        var sut = new JdbcErasureReceipts(repository);
        var receipt = ErasureReceipt.builder().organizationId("acme").surrogateId("erased:abc").erasedAt(ERASED_AT)
                .deletedRowCount(12).clearedRowCount(3).anonymisedRowCount(7).mediaResidue(2).build();

        // when
        sut.record(receipt);

        // then
        var captor = ArgumentCaptor.forClass(ErasureReceiptEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(row -> {
            assertThat(row.getId()).isNull();
            assertThat(row.getOrganizationId()).isEqualTo("acme");
            assertThat(row.getSurrogateId()).isEqualTo("erased:abc");
            assertThat(row.getErasedAt()).isEqualTo(ERASED_AT);
            assertThat(row.getDeletedRowCount()).isEqualTo(12);
            assertThat(row.getClearedRowCount()).isEqualTo(3);
            assertThat(row.getAnonymisedRowCount()).isEqualTo(7);
            assertThat(row.getMediaResidue()).isEqualTo(2);
        });
    }
}
