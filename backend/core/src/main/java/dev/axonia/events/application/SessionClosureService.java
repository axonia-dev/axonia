package dev.axonia.events.application;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionClosure;
import dev.axonia.events.api.SessionClosureRequest;
import dev.axonia.shared.api.error.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * The close and the follow-up an organizer gives one after the other in the session page, as one transaction. Both
 * steps keep their own rules and their own organizer check in {@link SessionService}; this only adds what the two
 * together owe a caller with no page to step through: nothing is written unless all of it can be.
 *
 * <p>
 * The follow-up runs even with nothing to record, because it is also what marks the slots' topics done: a session
 * closed as held here must end in the same state whether or not a recording came with it.
 */
@Service
@RequiredArgsConstructor
public class SessionClosureService implements SessionClosure {

    /** The width of {@code community_session.recording_url}. */
    static final int MAX_RECORDING_URL_LENGTH = 2048;

    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$");

    private final SessionService sessionService;

    @Override
    @Transactional
    public Session closeAsHeld(Long sessionId, SessionClosureRequest request) {

        var recordingUrl = strippedOrNull(request.recordingUrl());
        requireRecordableUrl(recordingUrl);

        sessionService.close(sessionId, new CloseSessionRequest(true, request.wrapUp()));
        sessionService.recordFollowUp(sessionId,
                new FollowUpRequest(strippedOrNull(request.notes()), recordingUrl, List.of()));
        return sessionService.getDetail(sessionId);
    }

    private static void requireRecordableUrl(String recordingUrl) {

        if (recordingUrl == null) {
            return;
        }
        if (!HTTP_URL.matcher(recordingUrl).matches() || recordingUrl.length() > MAX_RECORDING_URL_LENGTH) {
            throw new ValidationException("INVALID_RECORDING_URL",
                    "recordingUrl must be an http(s) URL of at most " + MAX_RECORDING_URL_LENGTH + " characters.");
        }
    }

    private static String strippedOrNull(String value) {

        return value == null || value.isBlank() ? null : value.strip();
    }
}
