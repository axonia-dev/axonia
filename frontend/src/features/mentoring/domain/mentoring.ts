import { z } from "zod";

export const roleSchema = z.enum(["MENTOR", "MENTEE"]);
export type MentoringRole = z.infer<typeof roleSchema>;

export const availabilitySchema = z.object({
  takingMentees: z.boolean(),
  menteeLimit: z.number().int().nullable(),
  lookingForMentor: z.boolean(),
});
export type Availability = z.infer<typeof availabilitySchema>;

export const directoryPersonSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
  areas: z.array(z.string()),
  proposable: z.boolean(),
  proposalState: z
    .enum(["PROPOSAL_SENT", "PROPOSAL_RECEIVED", "DECLINED_RECENTLY", "PAIRED"])
    .nullable(),
  /** The day a declined sender may propose again, for a person declined recently. */
  proposalsOpenOn: z.string().nullable(),
});

export type DirectoryPerson = z.infer<typeof directoryPersonSchema>;

const pairedPersonSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
});

/** An ongoing pairing its two people agreed to show, as every member of the community sees it. */
const shownPairingSchema = z.object({
  id: z.number().int(),
  mentor: pairedPersonSchema,
  mentee: pairedPersonSchema,
  /** The pairing's first day, a calendar day. */
  startedOn: z.string(),
});
export type ShownPairing = z.infer<typeof shownPairingSchema>;

export const mentoringPageSchema = z.object({
  archived: z.boolean(),
  mine: availabilitySchema,
  mentors: z.array(directoryPersonSchema),
  mentees: z.array(directoryPersonSchema),
  canMentorOn: z.array(z.string()),
  wantsToGrowIn: z.array(z.string()),
  guidedStart: z.boolean(),
  /** In the server's neutral order, by mentor then mentee name. */
  pairings: z.array(shownPairingSchema),
});
export type MentoringPage = z.infer<typeof mentoringPageSchema>;

export const proposalSchema = z.object({
  id: z.number().int(),
  communitySlug: z.string(),
  communityName: z.string(),
  proposedBy: roleSchema,
  partnerId: z.string().nullable(),
  partnerName: z.string(),
  partnerAvatarUrl: z.string().nullable(),
  topics: z.array(z.string()),
  message: z.string(),
  status: z.enum(["OPEN", "ACCEPTED", "DECLINED", "WITHDRAWN", "LAPSED"]),
  createdAt: z.string(),
  /** Until when the person who declined may reopen it; null once out of reach. */
  reopenableUntil: z.string().nullable(),
  /** The caller's day on which an open proposal lapses; null in every other state. */
  lapsesOn: z.string().nullable(),
});
export type ProposalStatus = Proposal["status"];
export type Proposal = z.infer<typeof proposalSchema>;

const topicStateSchema = z.enum(["TODO", "IN_PROGRESS", "DONE"]);
export type TopicState = z.infer<typeof topicStateSchema>;

export const pairingSummarySchema = z.object({
  id: z.number().int(),
  communitySlug: z.string(),
  communityName: z.string(),
  myRole: roleSchema,
  partnerId: z.string().nullable(),
  partnerName: z.string(),
  partnerGivenName: z.string().nullable(),
  partnerAvatarUrl: z.string().nullable(),
  startedAt: z.string(),
  endedAt: z.string().nullable(),
  /** The trail's stops in walking order: Done, then In progress, then To do. */
  stops: z.array(topicStateSchema),
  nowTopic: z.string().nullable(),
  nextMeeting: z.object({ on: z.string(), passed: z.boolean() }).nullable(),
  laterPlanned: z.number().int(),
  meetings: z.number().int(),
  notes: z.number().int(),
  lastMetOn: z.string().nullable(),
});
export type PairingSummary = z.infer<typeof pairingSummarySchema>;

export const myMentoringSchema = z.object({
  inAMentoringGuild: z.boolean(),
  received: z.array(proposalSchema),
  sent: z.array(proposalSchema),
  ongoing: z.array(pairingSummarySchema),
  ended: z.array(pairingSummarySchema),
  /** The caller's own calendar day, so the client never reads its clock for day counts. */
  today: z.string(),
});
export type MyMentoring = z.infer<typeof myMentoringSchema>;

export const presenceSchema = z.object({ inAMentoringGuild: z.boolean() });
export type MentoringPresence = z.infer<typeof presenceSchema>;

export const pointerSchema = z.object({
  id: z.number().int(),
  kind: z.enum(["SESSION", "ARTIFACT", "LINK"]),
  title: z.string().nullable(),
  href: z.string().nullable(),
  available: z.boolean(),
});

export type Pointer = z.infer<typeof pointerSchema>;

export const topicSchema = z.object({
  id: z.number().int(),
  label: z.string(),
  area: z.string().nullable(),
  state: topicStateSchema,
  position: z.number().int(),
  pointers: z.array(pointerSchema),
});

export const noteSchema = z.object({
  id: z.number().int(),
  authorId: z.string(),
  authorName: z.string(),
  authorAvatarUrl: z.string().nullable(),
  mine: z.boolean(),
  body: z.string(),
  meetingOn: z.string(),
  topicIds: z.array(z.number().int()),
  writtenAt: z.string(),
  editedAt: z.string().nullable(),
  pointers: z.array(pointerSchema),
});

export type Topic = z.infer<typeof topicSchema>;
export type Note = z.infer<typeof noteSchema>;

export const plannedMeetingSchema = z.object({
  id: z.number().int(),
  on: z.string(),
  topicIds: z.array(z.number().int()),
  plannedById: z.string().nullable(),
  plannedByName: z.string(),
  passed: z.boolean(),
});
export type PlannedMeeting = z.infer<typeof plannedMeetingSchema>;

/** Whether the community sees the pairing: Shown and Private are final, Asking waits on both people. */
const showingSchema = z.object({
  visibility: z.enum(["SHOWN", "ASKING", "PRIVATE"]),
  iSaidYes: z.boolean(),
});
export type Showing = z.infer<typeof showingSchema>;

export const journalSchema = z.object({
  pairing: pairingSummarySchema,
  writable: z.boolean(),
  /** The caller's own calendar day, and the pairing's first day in it. */
  today: z.string(),
  startedOn: z.string(),
  topics: z.array(topicSchema),
  notes: z.array(noteSchema),
  /** Every meeting not yet held, passed and upcoming, day ascending. */
  meetings: z.array(plannedMeetingSchema),
  wordsWritten: z.boolean(),
  canWriteWords: z.boolean(),
  myWords: z.string().nullable(),
  showing: showingSchema,
});
export type Journal = z.infer<typeof journalSchema>;

export const wordsSchema = z.object({
  pairingId: z.number().int(),
  communitySlug: z.string(),
  communityName: z.string(),
  menteeId: z.string().nullable(),
  menteeName: z.string(),
  menteeAvatarUrl: z.string().nullable(),
  startedAt: z.string(),
  endedAt: z.string().nullable(),
  body: z.string(),
  writtenAt: z.string(),
  /** When the mentor opened the envelope; changed words seal it again. */
  openedAt: z.string().nullable(),
});
export type Words = z.infer<typeof wordsSchema>;

export const BOARD_ORDER: readonly TopicState[] = [
  "TODO",
  "IN_PROGRESS",
  "DONE",
];

/** One column of the board, in its order. */
export function boardColumn(
  topics: readonly Topic[],
  state: TopicState,
): Topic[] {
  return topics
    .filter((topic) => topic.state === state)
    .sort((a, b) => a.position - b.position);
}

/**
 * The board after one sticker moves, as the server will number it: every column dense from 0, a
 * position past the end of its column meaning last, ordered To do, In progress, Done.
 */
export function placeTopic(
  topics: readonly Topic[],
  topicId: number,
  placement: { state: TopicState; position: number },
): Topic[] {
  const moving = topics.find((topic) => topic.id === topicId);
  if (moving === undefined) return [...topics];
  const rest = topics.filter((topic) => topic.id !== topicId);
  return BOARD_ORDER.flatMap((state) => {
    const slots = boardColumn(rest, state);
    if (state === placement.state) {
      const at = Math.max(0, Math.min(placement.position, slots.length));
      slots.splice(at, 0, { ...moving, state });
    }
    return slots.map((topic, position) => ({ ...topic, position }));
  });
}
