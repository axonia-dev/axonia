package dev.axonia.privacy.application;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.community.api.Memberships;
import dev.axonia.privacy.application.port.out.ErasureReceipts;
import dev.axonia.privacy.domain.ErasureReceipt;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;

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
    private final Memberships membershipService = mock(Memberships.class);
    private final ErasureReceipts receipts = mock(ErasureReceipts.class);

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

        var captor = ArgumentCaptor.forClass(ErasureReceipt.class);
        verify(receipts).record(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.organizationId()).isEqualTo(ORG);
        assertThat(saved.erasedAt()).isEqualTo(ERASED_AT);
        assertThat(saved.surrogateId()).startsWith("erased:");
        assertThat(saved.deletedRowCount()).isEqualTo(12);
        assertThat(saved.clearedRowCount()).isEqualTo(22);
        assertThat(saved.anonymisedRowCount()).isEqualTo(33);
        assertThat(saved.mediaResidue()).isEqualTo(44);
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
