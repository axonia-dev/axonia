package dev.axonia.mentoring.adapter.out.communication;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.communication.api.CommunicationPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessage;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.shared.api.text.Initials;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * The mentoring notices. The guild is the sender, as on every other community message, and the headline names the other
 * person. A proposal and a meeting reminder are mailed as well; the others reach the panel alone, so their template is
 * a name no mail is rendered from. A proposal's line is its topics and a decline's its first topic, since the panel
 * already shows the guild as the entry's community. No notice carries a proposal's message or a mentee's words: only
 * the two people read those, where they wrote them. A decline and a reopening carry their moment in the subject, so a
 * proposal declined, reopened and declined again is told each time, and a decline still waiting is withdrawn alone.
 */
@Component("mentoringNoticesAdapter")
@RequiredArgsConstructor
public class MentoringNoticesAdapter implements MentoringNotices {

    static final String PROPOSAL_TEMPLATE = "mentoring-proposal";

    static final String REMINDER_TEMPLATE = "mentoring-meeting-reminder";

    static final String PARTNER_FACT = "partner";

    static final String IN_APP_TEMPLATE = "mentoring";

    static final String SENDER_FACT = "sender";

    static final String HEADLINE_FACT = "sessionTitle";

    static final String TOPICS_FACT = "topics";

    public static final String LINE_FACT = "line";

    public static final String LINK_FACT = "mentoringUrl";

    /** The shell's neutral chip surface and body ink, as the roundup's sender monogram uses. */
    private static final String TINT_BACKGROUND = "#f6f0ec";
    private static final String TINT_FOREGROUND = "#211a26";

    /** A meeting's day as a notice names it, "Thursday 19 November". */
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

    private final CommunicationPort communication;
    private final PublicUrls publicUrls;

    @Override
    public void proposed(Proposal proposal, String communityName, String proposerName) {

        var facts = facts(communityName, proposerName + " proposes mentoring", String.join(", ", proposal.topics()),
                myMentoringUrl());
        facts.put(TOPICS_FACT, List.copyOf(proposal.topics()));
        communication.enqueue(OutboundMessage.builder().type(MessageType.MENTORING_PROPOSAL)
                .subjectRef(MentoringSubject.proposal(proposal.id(), proposal.senderId()).ref())
                .recipientUserIds(List.of(proposal.receiverId())).template(PROPOSAL_TEMPLATE)
                .aboutUserId(proposal.senderId()).facts(Map.copyOf(facts)).build());
    }

    @Override
    public void declined(Proposal proposal, String communityName, String declinerName) {

        inApp(MessageType.MENTORING_DECLINED, theDecline(proposal), proposal.senderId(), facts(communityName,
                declinerName + " answered: not this time", proposal.topics().getFirst(), myMentoringUrl()));
    }

    @Override
    public boolean withdrawDeclined(Proposal declined) {

        return communication.withdrawPending(MessageType.MENTORING_DECLINED, theDecline(declined).ref()) > 0;
    }

    /** A decline's subject names its answer's moment, so withdrawing it never reaches an earlier decline's notice. */
    private static MentoringSubject theDecline(Proposal proposal) {

        var occasion = String.valueOf(proposal.answeredAt().toEpochMilli());
        return MentoringSubject.proposal(proposal.id(), occasion, proposal.receiverId());
    }

    @Override
    public void reopened(Proposal proposal, String communityName, String reopenerName) {

        var occasion = String.valueOf(proposal.reopenedAt().toEpochMilli());
        inApp(MessageType.MENTORING_PROPOSAL_REOPENED,
                MentoringSubject.proposal(proposal.id(), occasion, proposal.receiverId()), proposal.senderId(),
                facts(communityName, reopenerName + " reopened a proposal", proposal.topics().getFirst(),
                        myMentoringUrl()));
    }

    @Override
    public void ended(Pairing pairing, String communityName, String endedByName, String recipientId) {

        var toMentor = recipientId.equals(pairing.mentorUserId());
        var endedBy = toMentor ? pairing.menteeUserId() : pairing.mentorUserId();
        var line = toMentor ? "The journal stays readable" : "A postcard to " + endedByName + " waits in the journal";
        inApp(MessageType.MENTORING_ENDED, MentoringSubject.pairing(pairing.id(), endedBy), recipientId,
                facts(communityName, "Mentoring with " + endedByName + " ended", line, journalUrl(pairing)));
    }

    @Override
    public void wordsReceived(Pairing pairing, String communityName, String menteeName) {

        inApp(MessageType.MENTORING_WORDS_RECEIVED, MentoringSubject.pairing(pairing.id(), pairing.menteeUserId()),
                pairing.mentorUserId(),
                facts(communityName, "Words from " + menteeName, "Nobody else sees them", journalUrl(pairing)));
    }

    @Override
    public void meetingSet(Pairing pairing, String communityName, String setByName, String recipientId) {

        var occasion = String.valueOf(pairing.nextMeetingSetAt().toEpochMilli());
        inApp(MessageType.MENTORING_MEETING_SET,
                MentoringSubject.pairing(pairing.id(), occasion, pairing.nextMeetingSetBy()), recipientId,
                facts(communityName, setByName + " set the next meeting", DAY.format(pairing.nextMeetingOn()),
                        journalUrl(pairing)));
    }

    @Override
    public void meetingReminder(Pairing pairing, String communityName, String partnerName, String recipientId) {

        var partnerId = recipientId.equals(pairing.mentorUserId()) ? pairing.menteeUserId() : pairing.mentorUserId();
        var subject = MentoringSubject.pairing(pairing.id(), pairing.nextMeetingOn().toString(), partnerId);
        var facts = facts(communityName, "Mentoring with " + partnerName + " tomorrow",
                DAY.format(pairing.nextMeetingOn()), journalUrl(pairing));
        facts.put(PARTNER_FACT, partnerName);
        communication.enqueue(OutboundMessage.builder().type(MessageType.MENTORING_MEETING_REMINDER)
                .subjectRef(subject.ref()).recipientUserIds(List.of(recipientId)).template(REMINDER_TEMPLATE)
                .aboutUserId(partnerId).facts(Map.copyOf(facts)).build());
    }

    private void inApp(MessageType type, MentoringSubject subject, String recipientId, Map<String, Object> facts) {

        if (recipientId == null) {
            throw new IllegalArgumentException("A mentoring notice needs a recipient");
        }
        communication.enqueue(OutboundMessage.builder().type(type).subjectRef(subject.ref())
                .recipientUserIds(List.of(recipientId)).template(IN_APP_TEMPLATE).inAppOnly(true)
                .aboutUserId(subject.aboutUserId()).facts(Map.copyOf(facts)).build());
    }

    private String myMentoringUrl() {

        return publicUrls.publicBaseUrl() + "/me/mentoring";
    }

    private String journalUrl(Pairing pairing) {

        return publicUrls.publicBaseUrl() + "/me/mentoring/" + pairing.id();
    }

    private static Map<String, Object> facts(String communityName, String headline, String line, String url) {

        var facts = new LinkedHashMap<String, Object>();
        facts.put(SENDER_FACT, Map.of("name", communityName, "initials", Initials.of(communityName), "tintBackground",
                TINT_BACKGROUND, "tintForeground", TINT_FOREGROUND));
        facts.put(HEADLINE_FACT, headline);
        facts.put(LINE_FACT, line);
        facts.put(LINK_FACT, url);
        return facts;
    }
}
