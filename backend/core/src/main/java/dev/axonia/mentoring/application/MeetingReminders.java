package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.MentoringNotices.MeetingReminder;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.Pairing;
import lombok.RequiredArgsConstructor;

/**
 * Reminds both people of a pairing's next meeting, each from 09:00 on their own day before it. Every zone's day before
 * lies within two days of today in UTC, so that window is all one run reads. A run repeats safely: the reminder's
 * reference carries the day, and the outbox keeps one message per reference. A pairing missing a side reminds nobody.
 */
@Service
@RequiredArgsConstructor
public class MeetingReminders {

    private final PairingRepository pairings;
    private final MemberClocks memberClocks;
    private final CommunityAccess communityAccess;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final Clock clock;

    @Transactional
    public void remindDue(String org) {

        var now = clock.instant();
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var due = pairings.withNextMeetingBetween(org, today, today.plusDays(2)).stream()
                .filter(pairing -> pairing.mentorUserId() != null && pairing.menteeUserId() != null).toList();
        if (due.isEmpty()) {
            return;
        }
        var zones = memberClocks.zonesOf(org, due.stream()
                .flatMap(pairing -> Stream.of(pairing.mentorUserId(), pairing.menteeUserId())).distinct().toList());
        for (var pairing : due) {
            for (var recipientId : List.of(pairing.mentorUserId(), pairing.menteeUserId())) {
                var zone = Objects.requireNonNull(zones.get(recipientId));
                if (MeetingDays.reminderDue(pairing.nextMeetingOn(), pairing.nextMeetingSetAt(), now, zone)) {
                    remind(org, pairing, recipientId);
                }
            }
        }
    }

    private void remind(String org, Pairing pairing, String recipientId) {

        notices.meetingReminder(MeetingReminder.builder().organizationId(org).pairing(pairing)
                .guild(communityAccess.guildOf(org, pairing.communityId()))
                .partnerName(personDirectory.shortName(org, MentoringViews.partnerOf(pairing, recipientId)))
                .recipientId(recipientId).build());
    }
}
