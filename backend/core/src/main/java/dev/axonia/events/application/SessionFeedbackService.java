package dev.axonia.events.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.FeedbackAnswer;
import dev.axonia.events.api.SessionFeedbackView;
import dev.axonia.events.api.SessionFeedbackView.Result;
import dev.axonia.events.api.SessionFeedbackView.ResultState;
import dev.axonia.events.api.SessionFeedbackView.Role;
import dev.axonia.events.api.SessionFeedbackView.Window;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.FeedbackImportStore;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionFeedbackStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.WrapUpWindow;
import dev.axonia.events.domain.FeedbackRecord;
import dev.axonia.events.domain.FeedbackRules;
import dev.axonia.events.domain.FeedbackRules.WindowState;
import dev.axonia.events.domain.FeedbackTally;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionFeedbackService {

    private final SessionStore sessionStore;
    private final SessionFeedbackStore sessionFeedbackStore;
    private final FeedbackImportStore feedbackImportStore;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final WrapUpWindow wrapUpWindow;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SessionFeedbackView view(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        return viewOf(organizationId, session(organizationId, sessionId), currentUser.userId());
    }

    /** Outside one transaction: a lost insert race aborts its own, so the replacement needs a fresh one. */
    public SessionFeedbackView give(Long sessionId, FeedbackAnswer answer) {

        var organizationId = organizationContext.organizationId();
        var session = session(organizationId, sessionId);
        var userId = currentUser.userId();
        requireOpenTo(organizationId, session, userId);
        try {
            store(organizationId, sessionId, userId, answer);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
            store(organizationId, sessionId, userId, answer);
        }
        return viewOf(organizationId, session, userId);
    }

    private void requireOpenTo(String organizationId, SessionRecord session, String userId) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        if (role(session, userId) != Role.ATTENDEE) {
            throw new ForbiddenException("FEEDBACK_NOT_ELIGIBLE", "Only an attendee may give feedback");
        }
        if (feedbackImportStore.find(organizationId, session.getId()).isPresent()) {
            throw new ConflictException("FEEDBACK_IMPORTED", "This session holds imported feedback");
        }
        if (windowState(session) != WindowState.OPEN) {
            throw new ConflictException("FEEDBACK_WINDOW_CLOSED", "Feedback for this session is closed");
        }
    }

    private void store(String organizationId, Long sessionId, String userId, FeedbackAnswer answer) {

        var feedback = sessionFeedbackStore.findMine(organizationId, sessionId, userId).map(FeedbackRecord::toBuilder)
                .orElseGet(() -> FeedbackRecord.builder().organizationId(organizationId).sessionId(sessionId)
                        .userId(userId))
                .enjoyed(answer.enjoyed()).learned(answer.learned()).build();
        sessionFeedbackStore.save(feedback);
    }

    private SessionFeedbackView viewOf(String organizationId, SessionRecord session, String userId) {

        var maxAge = wrapUpWindow.maxAge();
        var window = FeedbackRules.windowState(session, maxAge, clock.instant());
        var role = role(session, userId);
        var mine = currentUser.delegated()
                ? null
                : sessionFeedbackStore.findMine(organizationId, session.getId(), userId)
                        .map(f -> new FeedbackAnswer(f.getEnjoyed(), f.getLearned())).orElse(null);
        var seesResult = role == Role.PRESENTER || communityAccess.canViewHealth(session.getCommunityId(), userId);
        return SessionFeedbackView.builder().window(Window.valueOf(window.name()))
                .closesAt(FeedbackRules.closesAt(session, maxAge).orElse(null)).role(role).mine(mine)
                .result(seesResult ? result(organizationId, session.getId(), window).orElse(null) : null).build();
    }

    private Optional<Result> result(String organizationId, Long sessionId, WindowState window) {

        return feedbackImportStore.find(organizationId, sessionId)
                .map(imported -> tallied(feedbackImportStore.tally(organizationId, sessionId), imported.getSource()))
                .or(() -> live(organizationId, sessionId, window));
    }

    private Optional<Result> live(String organizationId, Long sessionId, WindowState window) {

        return switch (window) {
            case OPEN -> Optional.of(Result.builder().state(ResultState.PENDING).answers(0).build());
            case CLOSED -> Optional.of(tallied(sessionFeedbackStore.tally(organizationId, sessionId), null));
            case NONE -> Optional.empty();
        };
    }

    private static Result tallied(FeedbackTally tally, String importedFrom) {

        if (tally.answers() < FeedbackRules.MIN_REVEAL) {
            return Result.builder().state(ResultState.TOO_FEW).answers(0).importedFrom(importedFrom).build();
        }
        return Result.builder().state(ResultState.REVEALED).answers(tally.answers()).enjoyed(tally.enjoyedAverage())
                .learned(tally.learnedAverage()).importedFrom(importedFrom).build();
    }

    private Role role(SessionRecord session, String userId) {

        var presented = SessionSections.nonNullSections(session.getSections()).stream()
                .flatMap(section -> SessionSections.nonNullSlots(section.getSlots()).stream())
                .flatMap(slot -> SessionSections.presenters(slot).stream())
                .anyMatch(presenter -> userId.equals(presenter.getUserId()));
        if (presented) {
            return Role.PRESENTER;
        }
        return communityAccess.isMember(session.getCommunityId(), userId) ? Role.ATTENDEE : Role.OTHER;
    }

    private WindowState windowState(SessionRecord session) {

        return FeedbackRules.windowState(session, wrapUpWindow.maxAge(), clock.instant());
    }

    private SessionRecord session(String organizationId, Long sessionId) {

        return sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }
}
