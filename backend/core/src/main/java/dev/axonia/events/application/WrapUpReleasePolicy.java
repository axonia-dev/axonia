package dev.axonia.events.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.WrapUpWindow;
import dev.axonia.events.domain.SessionRecord;
import lombok.RequiredArgsConstructor;

/**
 * The wrap-up release a session gets when it is closed as held, stored once as {@code wrapUpReleaseAt}.
 *
 * <p>
 * <b>The default.</b> The community's grace period after the close, unless the session {@link #recordsHistory}: then
 * {@code null}, a wrap-up that is visibly off rather than one that reaches every member weeks after the fact. The
 * reminder already treats a session backfilled into the past this way ({@link UpcomingReleasePolicy}); this is the same
 * answer for the message that follows the session. The grace period is measured from the close, not from the session,
 * so without this rule a session from two months ago closed today would mail the community within hours, and the
 * producer's own max age, which also counts from the release instant, would not stop it.
 *
 * <p>
 * <b>An explicit answer wins.</b> {@code false} suppresses the wrap-up whatever the session's age, which is how a
 * backfill of last week's session stays quiet. {@code true} releases it after the grace period even for an old session.
 * Either way the organizer can still move or clear the release afterwards through
 * {@link SessionReleaseService#setWrapUpRelease}.
 */
@Component
@RequiredArgsConstructor
public class WrapUpReleasePolicy {

    private final CommunityAccess communityAccess;
    private final WrapUpWindow wrapUpWindow;

    /** {@code wrapUp} is the organizer's answer, or {@code null} for the default above. */
    public Instant atClose(SessionRecord session, Boolean wrapUp, Instant now) {

        var sendsWrapUp = wrapUp == null ? !recordsHistory(session, now) : wrapUp;
        if (!sendsWrapUp) {
            return null;
        }
        var grace = communityAccess.communicationSettings(session.getCommunityId()).wrapUpGraceHours();
        return now.plus(grace, ChronoUnit.HOURS);
    }

    /**
     * Whether closing this session now records history rather than reports what just happened: it started further back
     * than any wrap-up is still news.
     */
    public boolean recordsHistory(SessionRecord session, Instant now) {

        return session.getScheduledAt().isBefore(now.minus(wrapUpWindow.maxAge()));
    }
}
