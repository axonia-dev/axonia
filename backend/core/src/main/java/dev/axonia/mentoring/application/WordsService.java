package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.WordsView;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.JournalRules;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Words;
import dev.axonia.mentoring.domain.WordsForMentor;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

/**
 * A mentee's words to the mentor. Only the mentee writes or takes them back, only the mentor reads and opens them, and
 * to anybody else the pairing does not exist. New words wait for the pairing to end and are written up to a year after;
 * words written earlier stay changeable meanwhile. A mentee who left the guild still writes.
 */
@Service
@RequiredArgsConstructor
public class WordsService {

    private static final String NO_SUCH_PAIRING = "No such pairing";

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final PairingRepository pairings;
    private final WordsRepository words;
    private final PersonDirectory personDirectory;
    private final MentoringNotices notices;
    private final Clock clock;

    /** The first words tell the mentor once; a change tells nobody. */
    @Transactional
    public void write(long pairingId, String body) {

        var org = requireOwnSignIn();
        var pairing = pairing(org, pairingId, true);
        var text = JournalRules.words(body);
        var now = now();
        requireWritable(pairing, words.find(org, pairingId).isPresent(), now);
        var created = words.upsert(org, Words.builder().pairingId(pairingId).body(text).writtenAt(now).build());
        if (created) {
            var me = currentUser.userId();
            notices.wordsReceived(pairing, MentoringGuild.of(communityAccess.communityById(pairing.communityId())),
                    personDirectory.resolve(List.of(me)).get(me).displayName());
        }
    }

    /** Taking words back is not bound by the window. */
    @Transactional
    public void takeBack(long pairingId) {

        var org = requireOwnSignIn();
        pairing(org, pairingId, true);
        words.delete(org, pairingId);
    }

    /** The mentor opens the envelope; only the first opening of each version is kept. */
    @Transactional
    public void open(long pairingId) {

        var org = requireOwnSignIn();
        pairing(org, pairingId, false);
        words.markOpened(org, pairingId, now());
    }

    /** What mentees wrote to the caller as mentor, newest first; reading is not bound by the window. */
    @Transactional(readOnly = true)
    public List<WordsView> mine() {

        var org = requireOwnSignIn();
        var received = words.toMentor(org, currentUser.userId());
        var people = personDirectory.resolve(
                received.stream().map(WordsForMentor::menteeUserId).filter(Objects::nonNull).distinct().toList());
        var communities = new HashMap<Long, MentoringCommunity>();
        return received.stream().map(w -> view(w, MentoringViews.person(people, w.menteeUserId()),
                communities.computeIfAbsent(w.communityId(), communityAccess::communityById))).toList();
    }

    private static WordsView view(WordsForMentor received, MentoringPerson mentee, MentoringCommunity community) {

        return WordsView.builder().pairingId(received.pairingId()).communitySlug(community.slug())
                .communityName(community.name()).menteeId(mentee.userId()).menteeName(mentee.displayName())
                .menteeAvatarUrl(mentee.avatarUrl()).startedAt(received.startedAt()).endedAt(received.endedAt())
                .body(received.body())
                .writtenAt(received.updatedAt() == null ? received.writtenAt() : received.updatedAt())
                .openedAt(received.openedAt()).build();
    }

    private String requireOwnSignIn() {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        return organizationContext.organizationId();
    }

    /** The pairing as its mentee, or as its mentor; anybody else, and the other role, find nothing. */
    private Pairing pairing(String org, long pairingId, boolean asMentee) {

        var me = currentUser.userId();
        return pairings.find(org, pairingId).filter(p -> me.equals(asMentee ? p.menteeUserId() : p.mentorUserId()))
                .orElseThrow(() -> new NotFoundException(NO_SUCH_PAIRING));
    }

    private static void requireWritable(Pairing pairing, boolean exists, Instant now) {

        if (MentoringWindows.isWordsNotYet(pairing, exists)) {
            throw new ConflictException("MENTORING_WORDS_NOT_YET", "Words are written once the pairing has ended");
        }
        if (!MentoringWindows.isWordsWritable(pairing, exists, now)) {
            throw new ConflictException("MENTORING_WORDS_CLOSED", "The words of an ended pairing close after a year");
        }
    }

    private Instant now() {

        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
