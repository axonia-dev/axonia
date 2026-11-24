import type {
  Availability,
  DirectoryPerson,
  Journal,
  MentorFace,
  MentoringPage,
  MyMentoring,
  Note,
  PairingSummary,
  PlannedMeeting,
  Pointer,
  Proposal,
  ShownPairing,
  Topic,
  Words,
} from "@/features/mentoring/domain/mentoring";

export const availability: Availability = {
  takingMentees: true,
  menteeLimit: 3,
  lookingForMentor: false,
};

export function personOf(
  overrides: Partial<DirectoryPerson> = {},
): DirectoryPerson {
  return {
    userId: "u-1",
    displayName: "Ada",
    avatarUrl: null,
    areas: ["kafka"],
    proposable: true,
    proposalState: null,
    proposalsOpenOn: null,
    ...overrides,
  };
}

export function pageOf(overrides: Partial<MentoringPage> = {}): MentoringPage {
  return {
    archived: false,
    mine: availability,
    mentors: [personOf()],
    mentees: [],
    canMentorOn: ["kafka"],
    wantsToGrowIn: ["testing"],
    guidedStart: false,
    pairings: [],
    otherMentors: { faces: [], total: 0 },
    listing: null,
    ...overrides,
  };
}

export function mentorFaceOf(overrides: Partial<MentorFace> = {}): MentorFace {
  return {
    userId: "u-8",
    displayName: "Aurélia Rochat",
    avatarUrl: null,
    status: "MENTORING_NOW",
    ...overrides,
  };
}

export function shownPairingOf(
  overrides: Partial<ShownPairing> = {},
): ShownPairing {
  return {
    mentor: { userId: "u-4", displayName: "Ana Keller", avatarUrl: null },
    mentee: { userId: "u-5", displayName: "Dan Ilić", avatarUrl: null },
    startedOn: "2026-10-06",
    ...overrides,
  };
}

export function proposalOf(overrides: Partial<Proposal> = {}): Proposal {
  return {
    id: 7,
    communitySlug: "platform",
    communityName: "Platform",
    proposedBy: "MENTEE",
    partnerId: "u-1",
    partnerName: "Ada",
    partnerAvatarUrl: null,
    topics: ["Giving feedback"],
    message: "Happy to pair up?",
    status: "OPEN",
    createdAt: "2026-11-16T10:00:00Z",
    reopenableUntil: null,
    lapsesOn: null,
    pairsShown: true,
    ...overrides,
  };
}

export function pairingOf(
  overrides: Partial<PairingSummary> = {},
): PairingSummary {
  return {
    id: 11,
    communitySlug: "platform",
    communityName: "Platform",
    myRole: "MENTEE",
    partnerId: "u-1",
    partnerName: "Ada",
    partnerGivenName: null,
    partnerAvatarUrl: null,
    startedAt: "2026-11-16T10:00:00Z",
    endedAt: null,
    stops: ["IN_PROGRESS"],
    nowTopic: null,
    nextMeeting: null,
    laterPlanned: 0,
    meetings: 0,
    notes: 0,
    lastMetOn: null,
    ...overrides,
  };
}

export function pointerOf(overrides: Partial<Pointer> = {}): Pointer {
  return {
    id: 5,
    kind: "SESSION",
    title: "Kafka basics",
    href: "/communities/platform/sessions/9",
    available: true,
    ...overrides,
  };
}

export function topicOf(overrides: Partial<Topic> = {}): Topic {
  return {
    id: 1,
    label: "Giving feedback",
    area: null,
    state: "IN_PROGRESS",
    position: 0,
    pointers: [],
    ...overrides,
  };
}

export function noteOf(overrides: Partial<Note> = {}): Note {
  return {
    id: 2,
    authorId: "u-2",
    authorName: "Noah",
    authorAvatarUrl: null,
    mine: true,
    body: "Met, covered unit tests.",
    meetingOn: "2026-11-16",
    topicIds: [],
    writtenAt: "2026-11-16T11:00:00Z",
    editedAt: null,
    pointers: [],
    ...overrides,
  };
}

export function plannedOf(
  overrides: Partial<PlannedMeeting> = {},
): PlannedMeeting {
  return {
    id: 8,
    on: "2026-11-19",
    topicIds: [],
    plannedById: "u-1",
    plannedByName: "Ada",
    passed: false,
    ...overrides,
  };
}

export function journalOf(overrides: Partial<Journal> = {}): Journal {
  return {
    pairing: pairingOf(),
    writable: true,
    today: "2026-11-16",
    startedOn: "2026-11-16",
    topics: [topicOf({ pointers: [pointerOf()] })],
    notes: [noteOf()],
    meetings: [],
    wordsWritten: false,
    canWriteWords: true,
    myWords: null,
    showing: { visibility: "PRIVATE", iSaidYes: false },
    ...overrides,
  };
}

export function wordsOf(overrides: Partial<Words> = {}): Words {
  return {
    pairingId: 11,
    communitySlug: "platform",
    communityName: "Platform",
    menteeId: "u-2",
    menteeName: "Noah",
    menteeAvatarUrl: null,
    startedAt: "2026-11-16T10:00:00Z",
    endedAt: null,
    body: "Thanks for the time.",
    writtenAt: "2026-11-20T10:00:00Z",
    openedAt: null,
    ...overrides,
  };
}

export const page: MentoringPage = pageOf();

export const proposal: Proposal = proposalOf();

export const pairing: PairingSummary = pairingOf();

export const mine: MyMentoring = {
  inAMentoringGuild: true,
  received: [],
  sent: [proposal],
  ongoing: [pairing],
  ended: [],
  today: "2026-11-17",
};

export const journal: Journal = journalOf();

export const words: Words = wordsOf();
