package dev.axonia.privacy.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.api.Memberships;
import dev.axonia.privacy.application.port.out.ErasureReceipts;
import dev.axonia.privacy.domain.ErasureReceipt;
import dev.axonia.privacy.domain.SurrogateIds;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * The atomic database half of erasing one member from one Organization: leave every community, run every module's
 * eraser, write the receipt, in ONE transaction. Must run inside an {@code OrganizationScope.runAs(orgId, ...)} - the
 * erasers rely on the connection's {@code app.current_org} as their outer isolation guarantee. A separate bean from
 * {@link dev.axonia.privacy.application.AccountErasure} so {@code @Transactional} actually applies - a self-invoked
 * method on a proxy gets no transaction, which is why the purge is its own bean.
 *
 * <p>
 * Leaving goes through {@link Memberships#leaveForErasure}, the community module's own leave path, rather than a direct
 * DELETE, because that path already drops the role binding, decrements the Organization's member count and schedules
 * the identity-provider membership removal after commit. Reimplementing it here would leave three counters wrong. There
 * is deliberately no {@code CommunityMemberDataEraser} in the erasers list this class also runs: the membership row is
 * already gone by the time the erasers run, so a separate {@code DELETE FROM memberships} would always affect zero
 * rows. The count of communities actually left is folded into {@link ErasureCounts#deleted()} directly, so the receipt
 * still reports it truthfully. What the leave path's own listeners delete, a hub member's role binding and the member's
 * declarations and areas to learn in each community left, is not counted: the erasers find those rows gone.
 */
@Component
@RequiredArgsConstructor
public class MemberPurge {

    private final List<MemberDataEraser> erasers;
    private final Memberships membershipService;
    private final ErasureReceipts receipts;

    @Transactional
    public ErasureCounts eraseFrom(String organizationId, String userId, Instant erasedAt) {

        var communityIds = membershipService.communityIdsOf(organizationId, userId);
        communityIds.forEach(communityId -> membershipService.leaveForErasure(organizationId, communityId, userId));

        var erasure = MemberErasure.builder().organizationId(organizationId).userId(userId)
                .surrogateId(SurrogateIds.mint()).erasedAt(erasedAt).build();
        var counts = erasers.stream().map(eraser -> eraser.erase(erasure))
                .reduce(ErasureCounts.NONE, ErasureCounts::plus).plus(new ErasureCounts(communityIds.size(), 0, 0, 0));

        receipts.record(ErasureReceipt.builder().organizationId(organizationId).surrogateId(erasure.surrogateId())
                .erasedAt(erasedAt).deletedRowCount(counts.deleted()).clearedRowCount(counts.cleared())
                .anonymisedRowCount(counts.anonymised()).mediaResidue(counts.mediaResidue()).build());
        return counts;
    }
}
