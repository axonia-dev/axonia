package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.NextMeeting;
import lombok.RequiredArgsConstructor;

/**
 * The one next meeting a pairing holds. Either person sets it, to a day from their own today on, or clears it; a new or
 * changed day tells the partner, and the same day again or a clearing tells nobody. An ended pairing holds none.
 */
@Service
@RequiredArgsConstructor
public class NextMeetingService {

    private final PairingParties parties;
    private final PairingRepository pairings;
    private final MemberClocks memberClocks;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final Clock clock;

    @Transactional
    public void set(long pairingId, LocalDate on) {

        var party = parties.write(pairingId);
        var today = MeetingDays.dayOf(clock.instant(), memberClocks.zoneOf(party.org(), party.me()));
        var day = MeetingDays.upcoming(on, today);
        var pairing = party.pairing();
        if (day.equals(pairing.nextMeetingOn())) {
            return;
        }
        var at = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        pairings.setNextMeeting(party.org(), pairingId, new NextMeeting(day, party.me(), at));
        var partner = MentoringViews.partnerOf(pairing, party.me());
        if (partner != null) {
            notices.meetingSet(
                    pairing.toBuilder().nextMeetingOn(day).nextMeetingSetBy(party.me()).nextMeetingSetAt(at).build(),
                    communityAccess.guildOf(party.org(), pairing.communityId()),
                    personDirectory.displayName(party.org(), party.me()), partner);
        }
    }

    @Transactional
    public void clear(long pairingId) {

        var party = parties.write(pairingId);
        pairings.clearNextMeeting(party.org(), pairingId);
    }
}
