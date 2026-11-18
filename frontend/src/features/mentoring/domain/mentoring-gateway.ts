import type {
  Availability,
  Journal,
  MentoringPage,
  MentoringPresence,
  MentoringRole,
  MyMentoring,
  PairingSummary,
  PlannedMeeting,
  Proposal,
  TopicState,
  Words,
} from "@/features/mentoring/domain/mentoring";

export interface ProposalInput {
  toUserId: string;
  asRole: MentoringRole;
  topics: string[];
  message: string;
}

export interface TopicInput {
  label: string;
  area: string | null;
}

export interface TopicPlacement {
  state: TopicState;
  /** Slot in the target column, from 0; past the end means last. */
  position: number;
}

export interface NoteInput {
  body: string;
  /** The day of the meeting the note is about; null means today. */
  meetingOn: string | null;
  topicIds: number[];
}

export interface PointerInput {
  topicId: number | null;
  noteId: number | null;
  url: string;
}

export interface MeetingInput {
  on: string;
  topicIds: number[];
}

export type ProposalAnswer = "accept" | "decline" | "withdraw" | "reopen";

/** Yes, keep private, or take a yes back while the partner has not answered. */
export type ShowingAnswer = "YES" | "NO" | "UNSAID";

export interface MentoringGateway {
  page(slug: string): Promise<MentoringPage>;
  setAvailability(
    slug: string,
    availability: Availability,
  ): Promise<Availability>;
  propose(slug: string, proposal: ProposalInput): Promise<Proposal>;
  mine(): Promise<MyMentoring>;
  /** The shell's cheap probe: whether the member has a reason to open My mentoring. */
  presence(): Promise<MentoringPresence>;
  /** Accepting yields the new pairing; declining, withdrawing or reopening yields nothing. */
  answer(id: number, answer: ProposalAnswer): Promise<PairingSummary | null>;
  journal(id: number): Promise<Journal>;
  end(id: number): Promise<void>;
  addTopic(pairingId: number, topic: TopicInput): Promise<void>;
  updateTopic(
    pairingId: number,
    topicId: number,
    topic: TopicInput,
  ): Promise<void>;
  removeTopic(pairingId: number, topicId: number): Promise<void>;
  placeTopic(
    pairingId: number,
    topicId: number,
    placement: TopicPlacement,
  ): Promise<void>;
  addNote(pairingId: number, note: NoteInput): Promise<void>;
  updateNote(pairingId: number, noteId: number, note: NoteInput): Promise<void>;
  removeNote(pairingId: number, noteId: number): Promise<void>;
  addPointer(pairingId: number, pointer: PointerInput): Promise<void>;
  removePointer(pairingId: number, pointerId: number): Promise<void>;
  writeWords(pairingId: number, body: string): Promise<void>;
  takeBackWords(pairingId: number): Promise<void>;
  /** The mentor opened the envelope of a mentee's words. */
  openWords(pairingId: number): Promise<void>;
  planMeeting(pairingId: number, input: MeetingInput): Promise<PlannedMeeting>;
  changeMeeting(
    pairingId: number,
    meetingId: number,
    input: MeetingInput,
  ): Promise<void>;
  removeMeeting(pairingId: number, meetingId: number): Promise<void>;
  myWords(): Promise<Words[]>;
  answerShowing(pairingId: number, answer: ShowingAnswer): Promise<void>;
}
