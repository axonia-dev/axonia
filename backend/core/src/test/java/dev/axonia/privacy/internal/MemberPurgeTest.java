package dev.axonia.privacy.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.ErasureCounts;
import dev.axonia.MemberDataEraser;
import dev.axonia.community.MembershipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberPurgeTest {

    private static final String ORG = "acme-hub";
    private static final String USER = "target-user";
    private static final Instant ERASED_AT = Instant.parse("2026-07-28T00:00:00Z");

    private final MemberDataEraser first = mock(MemberDataEraser.class);
    private final MemberDataEraser second = mock(MemberDataEraser.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final ErasureReceiptRepository receipts = mock(ErasureReceiptRepository.class);

    private final MemberPurge purge = new MemberPurge(List.of(first, second), membershipService, receipts);

    @Test
    void leavesEveryCommunityThenSumsEveryEraserPlusTheCommunitiesLeftAndWritesAllFourCountsToTheReceipt() {

        // given - two erasers with deliberately distinct counts in every slot, so a transposition would be caught,
        // plus one community the member must leave, folded into "deleted" since no eraser owns that row anymore
        when(membershipService.communityIdsOf(ORG, USER)).thenReturn(List.of(11L));
        when(first.erase(any())).thenReturn(new ErasureCounts(1, 2, 3, 4));
        when(second.erase(any())).thenReturn(new ErasureCounts(10, 20, 30, 40));

        // when
        var counts = purge.eraseFrom(ORG, USER, ERASED_AT);

        // then - the community was genuinely left before the erasers ran, not just called at some point
        var order = inOrder(membershipService, first, second);
        order.verify(membershipService).leaveForErasure(ORG, 11L, USER);
        order.verify(first).erase(any());
        order.verify(second).erase(any());

        // and every count landed in its own slot: deleted = 1 (community) + 1 + 10, never mixed with another column
        assertThat(counts.deleted()).isEqualTo(12);
        assertThat(counts.cleared()).isEqualTo(22);
        assertThat(counts.anonymised()).isEqualTo(33);
        assertThat(counts.mediaResidue()).isEqualTo(44);

        var captor = ArgumentCaptor.forClass(ErasureReceiptEntity.class);
        verify(receipts).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(ORG);
        assertThat(saved.getErasedAt()).isEqualTo(ERASED_AT);
        assertThat(saved.getSurrogateId()).startsWith("erased:");
        assertThat(saved.getDeletedRowCount()).isEqualTo(12);
        assertThat(saved.getClearedRowCount()).isEqualTo(22);
        assertThat(saved.getAnonymisedRowCount()).isEqualTo(33);
        assertThat(saved.getMediaResidue()).isEqualTo(44);
    }

    @Test
    void noCommunityToLeaveContributesNothingToDeleted() {

        // given - the erasers still ran (an enterprise-only membership case is out of scope here, but a hub member
        // with no community row left, e.g. a second erasure re-run, must not fabricate a deleted-community count)
        when(membershipService.communityIdsOf(ORG, USER)).thenReturn(List.of());
        when(first.erase(any())).thenReturn(new ErasureCounts(1, 0, 0, 0));
        when(second.erase(any())).thenReturn(new ErasureCounts(2, 0, 0, 0));

        // when
        var counts = purge.eraseFrom(ORG, USER, ERASED_AT);

        // then
        assertThat(counts.deleted()).isEqualTo(3);
    }
}
