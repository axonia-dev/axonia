package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.ListingRequest;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.domain.ListingChange;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

/** A mentor's own switch for being listed among one community's mentors, from the door or the one-time ask. */
@Service
@RequiredArgsConstructor
public class MentorListingService {

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final MentorListingRepository repository;
    private final Clock clock;

    @Transactional
    public void set(String slug, ListingRequest request) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var community = communityAccess.communityForSlug(slug);
        var userId = currentUser.userId();
        requireWritable(community, userId);
        repository.setSwitch(organizationContext.organizationId(),
                ListingChange.builder().communityId(community.id()).userId(userId)
                        .state(request.listed() ? ListingSwitch.ON : ListingSwitch.OFF)
                        .at(clock.instant().truncatedTo(ChronoUnit.MILLIS)).build());
    }

    private void requireWritable(MentoringCommunity community, String userId) {

        if (!communityAccess.isMember(community.id(), userId)) {
            throw new ForbiddenException("Only members set their listing");
        }
        if (!community.mentoringEnabled()) {
            throw new ConflictException("MENTORING_OFF", "Mentoring is switched off in this community");
        }
        if (community.archived()) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot change the listing in an archived community");
        }
    }
}
