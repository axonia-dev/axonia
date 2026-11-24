package dev.axonia.mentoring.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;

public interface CommunityAccess {

    MentoringCommunity communityForSlug(String slug);

    MentoringCommunity communityById(long communityId);

    /** Org-explicit, for callers that run off any request. */
    MentoringGuild guildOf(String org, long communityId);

    /** Every community of the organization that is not archived and takes mentoring, by id; org-explicit. */
    List<Long> mentoringCommunityIdsIn(String org);

    /** Whether the person belongs to a community that is taking mentoring proposals, in one query. */
    boolean inAMentoringGuild(String org, String userId);

    /** Every community the person belongs to, archived or not; org-explicit, for callers off any request. */
    List<Long> communityIdsOf(String org, String userId);

    /** Each community's slug by id, in the current organization; an id that does not resolve is absent. */
    Map<Long, String> slugsOf(Collection<Long> communityIds);

    boolean isMember(long communityId, String userId);

    /** Every member of the community, as user ids, in one query; org-explicit. */
    Set<String> memberIdsOf(String org, long communityId);

    /** Org-explicit, for callers that judge a member off any request. */
    boolean isMember(String org, long communityId, String userId);
}
