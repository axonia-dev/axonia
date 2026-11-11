package dev.axonia.events.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.FeedbackImportRequest;
import dev.axonia.events.api.FeedbackImportResult;
import dev.axonia.events.api.SessionFeedbackImports;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.FeedbackImportStore;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionFeedbackStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.WrapUpWindow;
import dev.axonia.events.domain.FeedbackImportRecord;
import dev.axonia.events.domain.FeedbackRules;
import dev.axonia.events.domain.FeedbackRules.WindowState;
import dev.axonia.events.domain.ImportedAnswer;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * Only history takes an import: a held session whose feedback window never opened, or opened and closed with no member
 * answer, so a session's result always has one origin.
 */
@Service
@RequiredArgsConstructor
public class SessionFeedbackImportService implements SessionFeedbackImports {

    /** The width of {@code session_feedback_import.source}. */
    static final int MAX_SOURCE_LENGTH = 80;

    static final int MAX_ANSWERS = 2000;

    private static final Set<Integer> SCALES = Set.of(5, 10);

    private final SessionStore sessionStore;
    private final SessionFeedbackStore feedbackStore;
    private final FeedbackImportStore importStore;
    private final SessionAuthorizer sessionAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final WrapUpWindow wrapUpWindow;
    private final Clock clock;

    @Override
    @Transactional
    public FeedbackImportResult importFeedback(Long sessionId, FeedbackImportRequest request) {

        var organizationId = organizationContext.organizationId();
        var session = sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        sessionAuthorizer.requireOrganizer(session, currentUser.userId());
        requireHistory(organizationId, session);

        var answers = answers(request);
        importStore.replace(FeedbackImportRecord.builder().organizationId(organizationId).sessionId(sessionId)
                .scale(request.scale()).source(source(request, answers)).answers(answers).createdAt(clock.instant())
                .build());

        return new FeedbackImportResult(sessionId, answers.stream().filter(a -> a.enjoyed() != null).count(),
                answers.stream().filter(a -> a.learned() != null).count(), answers.size() >= FeedbackRules.MIN_REVEAL);
    }

    private void requireHistory(String organizationId, SessionRecord session) {

        if (!"HELD".equals(session.getStatus())) {
            throw new ConflictException("SESSION_NOT_HELD", "Feedback can only be imported into a held session");
        }
        if (FeedbackRules.windowState(session, wrapUpWindow.maxAge(), clock.instant()) == WindowState.OPEN) {
            throw new ConflictException("FEEDBACK_WINDOW_OPEN",
                    "This session's feedback window is still open, so members answer it themselves");
        }
        if (feedbackStore.anyForSession(organizationId, session.getId())) {
            throw new ConflictException("FEEDBACK_HAS_ANSWERS",
                    "Members already answered this session, so it takes no import");
        }
    }

    private static List<ImportedAnswer> answers(FeedbackImportRequest request) {

        if (request.answers() == null) {
            throw new ValidationException("FEEDBACK_ANSWERS_REQUIRED",
                    "answers is required: an empty list removes the import");
        }
        if (request.answers().size() > MAX_ANSWERS) {
            throw new ValidationException("FEEDBACK_TOO_MANY_ANSWERS",
                    "One import carries at most " + MAX_ANSWERS + " answers");
        }
        if (request.answers().isEmpty()) {
            return List.of();
        }
        if (!SCALES.contains(request.scale())) {
            throw new ValidationException("FEEDBACK_SCALE_UNSUPPORTED", "scale must be 5 or 10");
        }
        return request.answers().stream().map(answer -> toImported(answer, request.scale())).toList();
    }

    private static ImportedAnswer toImported(FeedbackImportRequest.Answer answer, int scale) {

        if (answer == null || answer.enjoyed() == null && answer.learned() == null) {
            throw new ValidationException("FEEDBACK_ANSWER_EMPTY", "Each answer needs enjoyed, learned or both");
        }
        return new ImportedAnswer(onScale(answer.enjoyed(), scale), onScale(answer.learned(), scale));
    }

    private static Integer onScale(Integer value, int scale) {

        if (value != null && !FeedbackRules.isScore(value, scale)) {
            throw new ValidationException("FEEDBACK_SCORE_OUT_OF_SCALE",
                    "Each value must be between 1 and " + scale + ", the scale the form used");
        }
        return value;
    }

    private static String source(FeedbackImportRequest request, List<ImportedAnswer> answers) {

        var source = Objects.requireNonNullElse(request.source(), "").strip();
        if (answers.isEmpty()) {
            return source;
        }
        if (source.isEmpty() || source.length() > MAX_SOURCE_LENGTH) {
            throw new ValidationException("INVALID_FEEDBACK_SOURCE",
                    "source must name where the answers came from, in at most " + MAX_SOURCE_LENGTH + " characters");
        }
        return source;
    }
}
