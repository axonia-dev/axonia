package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.axonia.mentoring.api.Journal;
import dev.axonia.mentoring.api.NoteView;
import dev.axonia.mentoring.api.PlannedMeetingView;
import dev.axonia.mentoring.api.PointerView;
import dev.axonia.mentoring.api.ShowingView;
import dev.axonia.mentoring.api.TopicView;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.PointerTitles;
import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.MeetingPlan;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.Note;
import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerAddress;
import dev.axonia.mentoring.domain.PointerKind;
import dev.axonia.mentoring.domain.PointerTarget;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.TopicBoard;
import dev.axonia.mentoring.domain.Words;
import lombok.RequiredArgsConstructor;

/**
 * A journal as one of its two people reads it, with every pointer's current title and an in-app path into the guild its
 * target lives in today.
 */
@Component("mentoringJournalReads")
@RequiredArgsConstructor
class JournalReads {

    private final CommunityAccess communityAccess;
    private final JournalRepository journal;
    private final MeetingRepository meetings;
    private final WordsRepository words;
    private final PointerTitles pointerTitles;
    private final PersonDirectory personDirectory;
    private final MemberClocks memberClocks;
    private final Clock clock;

    Journal read(String org, String me, Pairing pairing) {

        var pointers = journal.pointersOf(org, pairing.id());
        var targets = pointerTitles.resolve(pointers);
        var slugs = communityAccess
                .slugsOf(targets.values().stream().map(PointerTarget::communityId).distinct().toList());
        Function<Predicate<Pointer>, List<PointerView>> pointersWhere = held -> pointers.stream().filter(held)
                .map(p -> pointer(p, targets, slugs)).toList();
        var notes = journal.notesOf(org, pairing.id());
        var noteTopics = journal.noteTopicsOf(org, pairing.id());
        var zone = memberClocks.zoneOf(org, me);
        var partnerId = MentoringViews.partnerOf(pairing, me);
        var planned = pairing.endedAt() == null ? meetings.meetingsOf(org, pairing.id()) : List.<PlannedMeeting>of();
        var people = personDirectory.resolve(Stream
                .of(Stream.of(partnerId), notes.stream().map(Note::authorUserId),
                        planned.stream().map(PlannedMeeting::plannedBy))
                .flatMap(Function.identity()).filter(Objects::nonNull).distinct().toList());
        var today = MeetingDays.dayOf(clock.instant(), zone);
        var mentee = me.equals(pairing.menteeUserId());
        var written = words.find(org, pairing.id());
        var topics = journal.topicsOf(org, pairing.id());
        var progress = new PairingProgress(TopicBoard.trailOrder(topics), NoteTally.of(notes), planned);
        return Journal.builder()
                .pairing(MentoringViews
                        .glance(MentoringViews.pairing(pairing, communityAccess.communityById(pairing.communityId()),
                                me, MentoringViews.person(people, partnerId)), pairing, progress, today)
                        .build())
                .today(today).startedOn(MeetingDays.dayOf(pairing.startedAt(), zone))
                .writable(pairing.endedAt() == null)
                .topics(TopicBoard.boardOrder(topics).stream()
                        .map(t -> new TopicView(t.id(), t.label(), t.area(), t.state().name(), t.position(),
                                pointersWhere.apply(p -> t.id().equals(p.topicId()))))
                        .toList())
                .notes(notes.stream()
                        .map(n -> note(n, people.get(n.authorUserId()), me)
                                .topicIds(noteTopics.getOrDefault(n.id(), List.of()))
                                .pointers(pointersWhere.apply(p -> n.id().equals(p.noteId()))).build())
                        .toList())
                .meetings(planned.stream().filter(m -> !m.held()).map(m -> meeting(m, people, today)).toList())
                .wordsWritten(written.isPresent())
                .canWriteWords(
                        mentee && MentoringWindows.isWordsWritable(pairing, written.isPresent(), clock.instant()))
                .myWords(mentee ? written.map(Words::body).orElse(null) : null)
                .showing(new ShowingView(pairing.showing().visibility().name(),
                        pairing.showing().saidYes(mentee ? Role.MENTEE : Role.MENTOR)))
                .build();
    }

    private static PlannedMeetingView meeting(PlannedMeeting meeting, Map<String, MentoringPerson> people,
            LocalDate today) {

        var plannedBy = MentoringViews.person(people, meeting.plannedBy());
        return PlannedMeetingView.builder().id(meeting.id()).on(meeting.on()).topicIds(meeting.topicIds())
                .plannedById(plannedBy.userId()).plannedByName(plannedBy.displayName())
                .passed(MeetingPlan.isOwed(meeting, today)).build();
    }

    private static NoteView.NoteViewBuilder note(Note note, MentoringPerson author, String me) {

        return NoteView.builder().id(note.id()).authorId(author.userId()).authorName(author.displayName())
                .authorAvatarUrl(author.avatarUrl()).mine(me.equals(note.authorUserId())).body(note.body())
                .meetingOn(note.meetingOn()).writtenAt(note.writtenAt()).editedAt(note.editedAt());
    }

    private static PointerView pointer(Pointer pointer, Map<Long, PointerTarget> targets, Map<Long, String> slugs) {

        var kind = pointer.kind();
        if (kind == PointerKind.LINK) {
            return new PointerView(pointer.id(), kind.name(), null, pointer.url(), true);
        }
        var target = targets.get(pointer.id());
        var slug = target == null ? null : slugs.get(target.communityId());
        return slug == null
                ? new PointerView(pointer.id(), kind.name(), null, null, false)
                : new PointerView(pointer.id(), kind.name(), target.title(),
                        PointerAddress.pathOf(kind, slug, pointer.targetId()), true);
    }
}
