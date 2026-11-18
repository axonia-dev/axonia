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
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringSubject;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.shared.api.text.Initials;
import dev.axonia.shared.api.web.OrganizationLinks;
import dev.axonia.shared.api.web.PublicUrls;
import lombok.RequiredArgsConstructor;

/**
 * The mentoring notices. The guild is the sender, as on every other community message, and the headline leads with what
 * happened, then names the other person: by the short name between two people who already know each other, by the whole
 * name on a proposal, which can come from anyone in the guild. A proposal and a meeting reminder are mailed as well;
 * the others reach the panel alone, so their template is a name no mail is rendered from. A proposal's line is its
 * topics and a decline's its first topic, since the panel already shows the guild as the entry's community. Every link
 * opens My mentoring or the journal inside that guild, where the notice came from, rather than the cross-community
 * page. No notice carries a proposal's message or a mentee's words: only the two people read those, where they wrote
 * them. A decline, a reopening and a meeting's planning carry their moment in the subject, so a proposal declined,
 * reopened and declined again is told each time, and a decline or a planning still waiting is withdrawn alone; a
 * planning's subject names whether it was a plan or a move as well.
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

    private static final String TOPIC_AFTER_DAY = " · ";

    private final CommunicationPort communication;
    private final PublicUrls publicUrls;

    @Override
    public void proposed(String organizationId, Proposal proposal, MentoringGuild guild, String proposerName) {

        var facts = facts(guild, "Mentoring proposed by " + proposerName, String.join(", ", proposal.topics()),
                OrganizationLinks.withOrganization(myMentoringUrl(guild), organizationId));
        facts.put(TOPICS_FACT, List.copyOf(proposal.topics()));
        communication.enqueue(OutboundMessage.builder().type(MessageType.MENTORING_PROPOSAL)
                .subjectRef(MentoringSubject.proposal(proposal.id(), proposal.senderId()).ref())
                .recipientUserIds(List.of(proposal.receiverId())).template(PROPOSAL_TEMPLATE)
                .aboutUserId(proposal.senderId()).facts(Map.copyOf(facts)).build());
    }

    @Override
    public void declined(Proposal proposal, MentoringGuild guild, String declinerName) {

        inApp(MessageType.MENTORING_DECLINED, theDecline(proposal), proposal.senderId(), facts(guild,
                "Not this time, from " + declinerName, proposal.topics().getFirst(), myMentoringUrl(guild)));
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
    public void reopened(Proposal proposal, MentoringGuild guild, String reopenerName) {

        var occasion = String.valueOf(proposal.reopenedAt().toEpochMilli());
        inApp(MessageType.MENTORING_PROPOSAL_REOPENED,
                MentoringSubject.proposal(proposal.id(), occasion, proposal.receiverId()), proposal.senderId(),
                facts(guild, "Proposal reopened by " + reopenerName, proposal.topics().getFirst(),
                        myMentoringUrl(guild)));
    }

    @Override
    public void ended(Pairing pairing, MentoringGuild guild, String endedByName, String recipientId) {

        var toMentor = recipientId.equals(pairing.mentorUserId());
        var endedBy = toMentor ? pairing.menteeUserId() : pairing.mentorUserId();
        var line = toMentor ? "The journal stays readable" : "A postcard to " + endedByName + " waits in the journal";
        inApp(MessageType.MENTORING_ENDED, MentoringSubject.pairing(pairing.id(), endedBy), recipientId,
                facts(guild, "Mentoring ended with " + endedByName, line, journalUrl(guild, pairing)));
    }

    @Override
    public void wordsReceived(Pairing pairing, MentoringGuild guild, String menteeName) {

        inApp(MessageType.MENTORING_WORDS_RECEIVED, MentoringSubject.pairing(pairing.id(), pairing.menteeUserId()),
                pairing.mentorUserId(),
                facts(guild, "Words from " + menteeName, "Nobody else sees them", journalUrl(guild, pairing)));
    }

    @Override
    public void meetingPlanned(MeetingNotice notice) {

        meetingNotice(notice, MeetingNoticeKind.PLANNED);
    }

    @Override
    public void meetingMoved(MeetingNotice notice) {

        meetingNotice(notice, MeetingNoticeKind.MOVED);
    }

    @Override
    public boolean withdrawMeetingPlanned(PlannedMeeting meeting) {

        return withdrawMeetingNotice(meeting, MeetingNoticeKind.PLANNED);
    }

    @Override
    public boolean withdrawMeetingMoved(PlannedMeeting meeting) {

        return withdrawMeetingNotice(meeting, MeetingNoticeKind.MOVED);
    }

    private boolean withdrawMeetingNotice(PlannedMeeting meeting, MeetingNoticeKind kind) {

        return communication.withdrawPending(MessageType.MENTORING_MEETING_SET, thePlanning(meeting, kind).ref()) > 0;
    }

    private void meetingNotice(MeetingNotice notice, MeetingNoticeKind kind) {

        var meeting = notice.meeting();
        var day = DAY.format(meeting.on());
        var line = notice.firstTopic() == null ? day : day + TOPIC_AFTER_DAY + notice.firstTopic();
        inApp(MessageType.MENTORING_MEETING_SET, thePlanning(meeting, kind), notice.recipientId(), facts(notice.guild(),
                kind.headline(notice.byName()), line, journalUrl(notice.guild(), notice.pairing())));
    }

    /**
     * A meeting's subject names its planning's kind and moment, so a move or a removal reaches that planning's notice
     * alone.
     */
    private static MentoringSubject thePlanning(PlannedMeeting meeting, MeetingNoticeKind kind) {

        return MentoringSubject.meeting(meeting.id(), kind.occasionOf(meeting), meeting.plannedBy());
    }

    /** A reminder names the meeting and its day, so a meeting moved since is reminded of for its new day alone. */
    @Override
    public void meetingReminder(MeetingReminder reminder) {

        var pairing = reminder.pairing();
        var meeting = reminder.meeting();
        var recipientId = reminder.recipientId();
        var partnerId = recipientId.equals(pairing.mentorUserId()) ? pairing.menteeUserId() : pairing.mentorUserId();
        var subject = MentoringSubject.meeting(meeting.id(), meeting.on().toString(), partnerId);
        var facts = facts(reminder.guild(), "Mentoring tomorrow with " + reminder.partnerName(),
                DAY.format(meeting.on()),
                OrganizationLinks.withOrganization(journalUrl(reminder.guild(), pairing), reminder.organizationId()));
        facts.put(PARTNER_FACT, reminder.partnerName());
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

    private String myMentoringUrl(MentoringGuild guild) {

        return publicUrls.publicBaseUrl() + "/communities/" + guild.slug() + "/me/mentoring";
    }

    private String journalUrl(MentoringGuild guild, Pairing pairing) {

        return myMentoringUrl(guild) + "/" + pairing.id();
    }

    private static Map<String, Object> facts(MentoringGuild guild, String headline, String line, String url) {

        var facts = new LinkedHashMap<String, Object>();
        facts.put(SENDER_FACT, Map.of("name", guild.name(), "initials", Initials.of(guild.name()), "tintBackground",
                TINT_BACKGROUND, "tintForeground", TINT_FOREGROUND));
        facts.put(HEADLINE_FACT, headline);
        facts.put(LINE_FACT, line);
        facts.put(LINK_FACT, url);
        return facts;
    }
}
