package dev.axonia.community.adapter.out.health;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.community.adapter.out.persistence.BucketCount;
import dev.axonia.community.adapter.out.persistence.JoinsPerBucketQuery;
import dev.axonia.community.adapter.out.persistence.MembershipRepository;
import dev.axonia.community.application.CommunityCapabilityService;
import dev.axonia.community.application.CommunityService;
import dev.axonia.community.application.port.out.OrganizationContext;
import dev.axonia.health.api.Buckets;
import dev.axonia.health.api.spi.MembershipSignals;
import lombok.RequiredArgsConstructor;

/** Community's implementation of the community-health signal port. */
@Component
@RequiredArgsConstructor
class MembershipSignalsAdapter implements MembershipSignals {

    private final CommunityService communityService;
    private final CommunityCapabilityService communityCapabilities;
    private final MembershipRepository membershipRepository;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional(readOnly = true)
    public long communityIdForSlug(String slug) {

        return communityService.getBySlug(slug).id();
    }

    @Override
    @Transactional(readOnly = true)
    public long memberCount(long communityId) {

        return membershipRepository.countByOrganizationIdAndCommunityId(organizationContext.organizationId(),
                communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public long joins(long communityId, Instant from, Instant to) {

        return membershipRepository.countJoins(organizationContext.organizationId(), communityId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> joinsPerBucket(long communityId, Instant from, Instant to, int buckets) {

        var query = JoinsPerBucketQuery.builder().organizationId(organizationContext.organizationId())
                .communityId(communityId).bucketSeconds(Buckets.secondsPerBucket(from, to, buckets))
                .lastBucket(buckets - 1).build();
        var rows = membershipRepository.countJoinsPerBucket(query, from, to);
        return Buckets.zeroFill(rows.stream().collect(Collectors.toMap(BucketCount::bucket, BucketCount::n)), buckets);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canViewHealth(long communityId, String userId) {

        return communityCapabilities.canViewHealth(communityId, userId);
    }
}
