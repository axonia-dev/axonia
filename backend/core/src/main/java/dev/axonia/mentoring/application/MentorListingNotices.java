package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentorRoster;
import lombok.RequiredArgsConstructor;

/**
 * Asks once each mentor only an ended, never shown pairing would list, and tells once each mentor listed by default, in
 * every community taking mentoring. A run repeats safely: each is marked when sent, and a marked one is never sent
 * again. Only present members of the community are asked or told.
 */
@Service
@RequiredArgsConstructor
public class MentorListingNotices {

    private final CommunityAccess communityAccess;
    private final PairingRepository pairings;
    private final MentorListingRepository listings;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final Clock clock;

    @Transactional
    public void sweep(String org) {

        for (var communityId : communityAccess.mentoringCommunityIdsIn(org)) {
            sweep(org, communityId);
        }
    }

    private void sweep(String org, long communityId) {

        var histories = pairings.mentorHistoriesIn(org, communityId);
        if (histories.isEmpty()) {
            return;
        }
        var members = communityAccess.memberIdsOf(org, communityId);
        var candidates = histories.stream().filter(h -> members.contains(h.mentorId())).toList();
        var present = personDirectory.presentOf(org, candidates.stream().map(MentorHistory::mentorId).toList());
        var switches = listings.in(org, communityId);
        var guild = communityAccess.guildOf(org, communityId);
        var now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        for (var history : candidates) {
            var mentorId = history.mentorId();
            if (!present.contains(mentorId)) {
                continue;
            }
            var listing = switches.getOrDefault(mentorId,
                    new MentorListing(communityId, mentorId, ListingSwitch.DEFAULT, null, null));
            var state = listing.state();
            if (MentorRoster.needsAsk(history, state) && listing.askedAt() == null) {
                notices.listingAsk(org, communityId, guild, mentorId);
                listings.markAsked(org, communityId, mentorId, now);
            } else if (isListedByDefault(history, state) && listing.toldAt() == null) {
                notices.listed(communityId, guild, mentorId);
                listings.markTold(org, communityId, mentorId, now);
            }
        }
    }

    private static boolean isListedByDefault(MentorHistory history, ListingSwitch state) {

        return state == ListingSwitch.DEFAULT && MentorRoster.status(history, state).isPresent();
    }
}
