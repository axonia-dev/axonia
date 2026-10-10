package dev.axonia.community.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.api.MemberCommunity;
import dev.axonia.community.domain.MembershipRecord;

public interface MembershipLedger {

    Optional<MembershipRecord> find(String organizationId, Long communityId, String userId);

    /** Every community the given member belongs to within an Organization. */
    List<MembershipRecord> findAllOf(String organizationId, String userId);

    /** The member's communities that are not archived, ordered by name ignoring case. */
    List<MemberCommunity> activeCommunitiesOf(String organizationId, String userId);

    long count(String organizationId, Long communityId);

    long countWithRole(String organizationId, Long communityId, CommunityRole role);

    Page<MembershipRecord> page(String organizationId, Long communityId, Pageable pageable);

    /** The roster as bare user ids. */
    List<String> memberUserIds(String organizationId, Long communityId);

    /** The community's holders of a given role, as bare user ids. */
    List<String> userIdsWithRole(String organizationId, Long communityId, CommunityRole role);

    /** The memberships of a named set of members, in no particular order. */
    List<MembershipRecord> findMembers(String organizationId, Long communityId, Collection<String> userIds);

    MembershipRecord save(MembershipRecord membership);

    void delete(MembershipRecord membership);
}
