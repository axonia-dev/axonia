package dev.axonia.privacy.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round trip only. Whether {@code erasure_receipt}'s RLS policies actually hold is proven against the real,
 * non-owner roles in {@code RlsIsolationTest} - {@code @DataJdbcTest} runs as the Testcontainers table owner, which
 * bypasses RLS by ownership, so a scope entered here would prove nothing.
 */
class ErasureReceiptRepositoryTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";

    @Autowired
    private ErasureReceiptRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void aReceiptPersistsAllFourCountsAndNamesNobody() {

        var erasedAt = Instant.parse("2026-07-27T10:00:00Z");
        var saved = repository
                .save(ErasureReceiptEntity.builder().organizationId(ORG).surrogateId("erased:abc").erasedAt(erasedAt)
                        .deletedRowCount(12).clearedRowCount(3).anonymisedRowCount(7).mediaResidue(2).build());

        var found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getOrganizationId()).isEqualTo(ORG);
        assertThat(found.getErasedAt()).isEqualTo(erasedAt);
        assertThat(found.getDeletedRowCount()).isEqualTo(12);
        assertThat(found.getClearedRowCount()).isEqualTo(3);
        assertThat(found.getAnonymisedRowCount()).isEqualTo(7);
        assertThat(found.getMediaResidue()).isEqualTo(2);

        assertThat(columnsOf(jdbc, "erasure_receipt"))
                .describedAs("a receipt must hold no column that could name a person")
                .doesNotContain("user_id", "display_name", "email", "subject");
    }
}
