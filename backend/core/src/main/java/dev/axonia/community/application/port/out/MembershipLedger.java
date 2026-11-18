package dev.axonia.community.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** Whether the member belongs to a community that is not archived and takes mentoring. */
    boolean inAMentoringCommunity(String organizationId, String userId);

    long count(String organizationId, Long communityId);

    long countWithRole(String organizationId, Long communityId, CommunityRole role);

    Page<MembershipRecord> page(String organizationId, Long communityId, Pageable pageable);

    /** The roster as bare user ids. */
    List<String> memberUserIds(String organizationId, Long communityId);

    /** The community's holders of a given role, as bare user ids. */
    List<String> userIdsWithRole(String organizationId, Long communityId, CommunityRole role);

    /** The memberships of a named set of members, in no particular order. */
    List<MembershipRecord> findMembers(String organizationId, Long communityId, Collection<String> userIds);

    /** Members per community across the Organization; a community with none is absent. */
    Map<Long, Long> memberCounts(String organizationId);

    /** Members who joined in {@code [from, to)}, per community; a community with none is absent. */
    Map<Long, Long> joinsWithin(String organizationId, Instant from, Instant to);

    /** Up to {@code perCommunity} member user ids of each community, the newest join first. */
    Map<Long, List<String>> newestMemberIds(String organizationId, Set<Long> communityIds, int perCommunity);

    MembershipRecord save(MembershipRecord membership);

    void delete(MembershipRecord membership);
}
