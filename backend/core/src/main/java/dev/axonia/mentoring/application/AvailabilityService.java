package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.AvailabilityRequest;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/** What a member offers or seeks in one guild. Off until the member sets it. */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    static final int MENTEE_LIMIT_MIN = 1;
    static final int MENTEE_LIMIT_MAX = 10;

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final AvailabilityRepository repository;
    private final Clock clock;

    @Transactional
    public Availability set(String slug, AvailabilityRequest request) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var community = communityAccess.communityForSlug(slug);
        var userId = currentUser.userId();
        requireWritable(community, userId);
        var limit = request.menteeLimit();
        requireInRange(limit);
        repository.upsert(organizationContext.organizationId(),
                dev.axonia.mentoring.domain.Availability.builder().communityId(community.id()).userId(userId)
                        .takingMentees(request.takingMentees()).menteeLimit(limit)
                        .lookingForMentor(request.lookingForMentor())
                        .updatedAt(clock.instant().truncatedTo(ChronoUnit.MILLIS)).build());
        return new Availability(request.takingMentees(), limit, request.lookingForMentor());
    }

    private void requireWritable(MentoringCommunity community, String userId) {

        if (!communityAccess.isMember(community.id(), userId)) {
            throw new ForbiddenException("Only members set their availability");
        }
        if (!community.mentoringEnabled()) {
            throw new ConflictException("MENTORING_OFF", "Mentoring is switched off in this community");
        }
        if (community.archived()) {
            throw new ConflictException("COMMUNITY_ARCHIVED", "Cannot change availability in an archived community");
        }
    }

    private void requireInRange(Integer limit) {

        if (limit != null && (limit < MENTEE_LIMIT_MIN || limit > MENTEE_LIMIT_MAX)) {
            throw new ValidationException("field_invalid",
                    "The mentee limit is between " + MENTEE_LIMIT_MIN + " and " + MENTEE_LIMIT_MAX);
        }
    }
}
