import type {
  ActionItem,
  ActionItemOutcome,
  CheckpointOutcome,
  RaiseActionItemInput,
  UpdateActionItemInput,
} from "@/features/events/domain/action-item";
import type { Attendance } from "@/features/events/domain/attendance";
import type { CalendarSubscription } from "@/features/events/domain/calendar-subscription";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import type { MemberZone } from "@/features/events/domain/member-zone";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { SectionType } from "@/features/events/domain/section-type";
import type { Cadence, Series } from "@/features/events/domain/series";
import type { Session, SessionStatus } from "@/features/events/domain/session";
import type { SlotGateway } from "@/features/events/domain/slot-gateway";
import type { Topic, TopicKind } from "@/features/events/domain/topic";
import type { TopicCounts } from "@/features/events/domain/topic-counts";
import type { Page } from "@/lib/page";

export interface PageParams {
  page?: number;
  size?: number;
}

export interface PastSessionParams extends PageParams {
  /** ISO instant, inclusive lower bound on scheduledAt. */
  from?: string;
  /** ISO instant, exclusive upper bound on scheduledAt. */
  to?: string;
  /** Narrows the window to one status. Absent reads every non-cancelled occurrence that has passed,
   *  skipped and unconfirmed ones included. */
  status?: SessionStatus;
}

/** One answer given during a session's follow-up pass: this action item, this outcome. Keyed by
 *  `actionItemId` wherever it is collected, so a duplicate decision for the same item is
 *  unrepresentable client-side - the backend refuses one with `DUPLICATE_DECISION`. */
interface CheckpointDecision {
  actionItemId: number;
  outcome: CheckpointOutcome;
}

/** What an organizer captured after a session: where the recording lives, their own note about what
 *  happened, and the room's answer for every open item pinned to this session. Notes and recording
 *  are replacements, and an empty string clears one. `decisions` is optional because the server
 *  reads an absent list as an empty one: a session that owed nothing decides nothing. */
export interface FollowUpInput {
  notes: string;
  recordingUrl: string;
  decisions?: CheckpointDecision[];
}

export interface SectionTypeInput {
  label: string;
  expectsPresenter: boolean;
  icon: string | null;
  accentColor: string | null;
}

export interface SectionTemplateInput {
  sectionTypeId: number;
  title: string | null;
  durationMinutes: number | null;
}

export interface SeriesInput {
  title: string;
  cadence: Cadence;
  startsOn: string;
  preferredTime?: string;
  timeZone: string;
  durationMinutes?: number;
  /**
   * The standing place every occurrence inherits, required rather than optional because
   * `PUT /series/{id}` replaces the series wholesale: an omitted key is a cleared place, and an
   * optional key lets a writer clear it by forgetting. Empty is how "no standing place" is said
   * on purpose. `preferredTime` and `durationMinutes` above carry the same hazard and are still
   * optional; closing those is a separate change with its own test surface.
   */
  place: MeetingPlace;
  defaultStructure: SectionTemplateInput[];
}

export interface TopicInput {
  kind: TopicKind;
  title: string;
  description?: string;
  tags?: string[];
}

/** The order keys the server recognises. Anything else is refused outright rather than ignored,
 *  so this is an allowlist and not a hint: `created_at` is newest first, `checkpoint` is
 *  checkpoint date then due date soonest first, and `updated_at,desc` is newest settled first.
 *  The direction is part of the key because the server reads `updated_at` alone as ascending. */
type ActionItemSort = "created_at" | "checkpoint" | "updated_at,desc";

export interface ActionItemListParams extends PageParams {
  outcome?: ActionItemOutcome;
  sort?: ActionItemSort;
  /** Restricts to the items raised at one held session - their history, not where they are
   *  answered. */
  originSessionId?: number;
  /** Restricts to the items this one session must answer for: pinned here, or unpinned and
   *  resolved to this session as whichever comes next. */
  checkpointSessionId?: number;
}

export interface TopicListParams extends PageParams {
  kind?: TopicKind;
  status?: "PROPOSED" | "SCHEDULED" | "DONE";
  /** Read-time lifecycle view; only "PAST" is served server-side. */
  phase?: "PAST";
  /** Free-text search over title/description (used by the Past topics tab). */
  q?: string;
}

/** A create, not a replacement, so an omitted key means "never had one" rather than "cleared".
 *  A one-off belongs to no series, so its own place is the only place it can ever have. */
export interface CreateOneOffInput {
  title: string;
  /** A civil date, an optional wall-clock time, and the zone that time is read in. Not an instant:
   *  an instant cannot express an occurrence with no hour, and the server composes in the series'
   *  own zone, which is what stops an organizer abroad moving a community's session by an offset
   *  nobody sees. `zone` is honoured only for a session with no series. */
  scheduledAt: { date: string; time: string | null; zone: string };
  durationMinutes?: number | null;
  place?: MeetingPlace;
  sections?: SectionTemplateInput[];
}

/**
 * `PUT /sessions/{id}` replaces the session's stored facts wholesale, so every field this
 * payload leaves out is cleared rather than kept. None of them is optional, deliberately: an
 * optional key on a full-replacement input means the next writer of this endpoint destroys a
 * field by not thinking about it, with both gates green. That is not hypothetical - it is
 * exactly how the meeting room was being wiped by the reschedule dialog and by every calendar
 * drag, undetected, until the two writers were found. Required keys make the next one a
 * compile error instead of a support ticket.
 *
 * `place` is the occurrence's own place and never the one it inherits: see
 * `Session.placeOverride`. Empty returns the occurrence to inheritance.
 */
export interface UpdateSessionInput {
  /** A civil date, an optional wall-clock time, and the zone that time is read in. Not an instant:
   *  an instant cannot express an occurrence with no hour, and the server composes in the series'
   *  own zone, which is what stops an organizer abroad moving a community's session by an offset
   *  nobody sees. `zone` is honoured only for a session with no series. */
  scheduledAt: { date: string; time: string | null; zone: string };
  durationMinutes: number | null;
  facilitatorUserId: string | null;
  place: MeetingPlace;
}

export interface CloseSessionInput {
  held: boolean;
}

export interface WrapUpReleaseInput {
  /** An instant to (re)schedule the wrap-up for, or null to suppress it permanently. */
  releaseAt: string | null;
}

/**
 * The organizer's answer to "when does the reminder go out", as the three mutually exclusive
 * payloads the endpoint takes: `{sendNow: true}`, `{releaseAt}`, or `{}` for no reminder at
 * all.
 *
 * A union rather than two optional keys, because the server refuses a payload carrying both
 * (`UPCOMING_RELEASE_AMBIGUOUS`) rather than resolving it by precedence. Spelling it this way
 * makes that payload unrepresentable here instead of merely never constructed by today's
 * callers, so the refusal can only ever be reached by a client that is not this one.
 *
 * "Send now" is a flag and not the browser's clock, unlike `WrapUpReleaseInput`: the wrap-up's
 * server clamps a past instant up to its own, while the reminder's refuses one outright,
 * so a browser running two seconds slow would have its send-now rejected as a
 * pick in the past. Saying "now" and letting the server stamp its own clock takes skew out of
 * the contract.
 */
export type UpcomingReleaseInput =
  | { sendNow: true; releaseAt?: never }
  | { sendNow?: never; releaseAt: string }
  | { sendNow?: never; releaseAt?: never };

/**
 * The two member-facing session messages an organizer releases, as the wire codes the API
 * takes - the backend's `ReleasableMessage`, narrowed from `MessageType`. The organizer-only
 * awaiting-close nudge is deliberately absent: it stays automatic, so there
 * is no intent to control and nothing to preview.
 */
export type ReleaseMessageType = "session_upcoming" | "session_wrap_up";

/** What `GET .../message-preview?type=...` answers with: the subject and HTML a send of that
 *  message type would produce for the session right now, rendered for the previewing member. */
interface MessagePreview {
  subject: string;
  html: string;
}

export interface EventsGateway extends SlotGateway {
  /** The zones this community's members read, and how many read each. Members with no clock are
   *  absent; a community nobody has measured answers with an empty list. */
  memberZones(slug: string): Promise<MemberZone[]>;
  listSectionTypes(): Promise<SectionType[]>;
  createSectionType(input: SectionTypeInput): Promise<SectionType>;
  updateSectionType(id: number, input: SectionTypeInput): Promise<SectionType>;
  deleteSectionType(id: number, reassignToId?: number): Promise<void>;
  recordFollowUp(sessionId: number, input: FollowUpInput): Promise<Session>;
  listSeries(slug: string, params: PageParams): Promise<Page<Series>>;
  startSeries(slug: string, input: SeriesInput): Promise<Series>;
  updateSeries(id: number, input: SeriesInput): Promise<Series>;
  endSeries(id: number): Promise<Series>;
  getSchedule(
    slug: string,
    fromIso: string,
    toIso: string,
  ): Promise<ScheduleEntry[]>;
  getScheduleActivity(slug: string): Promise<string[]>;
  ensureOccurrence(seriesId: number, occurrenceDate: string): Promise<Session>;
  skipOccurrence(seriesId: number, occurrenceDate: string): Promise<Session>;
  unskipOccurrence(seriesId: number, occurrenceDate: string): Promise<Session>;
  createOneOff(slug: string, input: CreateOneOffInput): Promise<Session>;
  updateSession(id: number, input: UpdateSessionInput): Promise<Session>;
  getSession(id: number): Promise<Session>;
  listUpcoming(slug: string, params: PageParams): Promise<Page<Session>>;
  listPast(slug: string, params: PastSessionParams): Promise<Page<Session>>;
  cancelSession(sessionId: number): Promise<Session>;
  closeSession(sessionId: number, input: CloseSessionInput): Promise<Session>;
  setWrapUpRelease(
    sessionId: number,
    input: WrapUpReleaseInput,
  ): Promise<Session>;
  setUpcomingRelease(
    sessionId: number,
    input: UpcomingReleaseInput,
  ): Promise<Session>;
  getMessagePreview(
    slug: string,
    sessionId: number,
    messageType: ReleaseMessageType,
  ): Promise<MessagePreview>;
  listAttendance(
    sessionId: number,
    params: PageParams,
  ): Promise<Page<Attendance>>;
  signalGoing(sessionId: number): Promise<Attendance>;
  unsignalGoing(sessionId: number): Promise<void>;
  listTopics(slug: string, params: TopicListParams): Promise<Page<Topic>>;
  getTopicCounts(slug: string): Promise<TopicCounts>;
  proposeTopic(slug: string, input: TopicInput): Promise<Topic>;
  voteTopic(topicId: number): Promise<Topic>;
  unvoteTopic(topicId: number): Promise<void>;
  editTopic(topicId: number, input: TopicInput): Promise<Topic>;
  deleteTopic(topicId: number): Promise<void>;
  returnToBacklog(topicId: number): Promise<void>;
  listActionItems(
    slug: string,
    params: ActionItemListParams,
  ): Promise<Page<ActionItem>>;
  raiseActionItem(
    slug: string,
    input: RaiseActionItemInput,
  ): Promise<ActionItem>;
  editActionItem(id: number, input: UpdateActionItemInput): Promise<ActionItem>;
  deleteActionItem(id: number): Promise<void>;
  closeActionItem(id: number): Promise<ActionItem>;
  dropActionItem(id: number): Promise<ActionItem>;
  reopenActionItem(id: number): Promise<ActionItem>;
  /** How the subscribe dialog loads: the member's own subscription, or null when they have
   *  never subscribed. Null is an answer, not a failure, and the dialog offers to mint one. */
  getCalendarSubscription(slug: string): Promise<CalendarSubscription | null>;
  /** Mints the subscription, which is the only moment its URL is ever readable. Find-or-create
   *  on the server, so a second call answers with the same subscription and no URL. */
  subscribeToCalendar(slug: string): Promise<CalendarSubscription>;
  /** Mints a new token in place, which stops every calendar still polling the old URL. */
  resetCalendarSubscription(slug: string): Promise<CalendarSubscription>;
  addSection(sessionId: number, input: SectionTemplateInput): Promise<Session>;
  updateSection(
    sessionId: number,
    sectionId: number,
    input: SectionTemplateInput,
  ): Promise<Session>;
  removeSection(sessionId: number, sectionId: number): Promise<Session>;
  reorderSections(sessionId: number, sectionIds: number[]): Promise<Session>;
}
