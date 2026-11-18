import { E2E_USER_ID } from "./api";
import { community, shellOfCommunity, SLUG } from "./community-home";

export const LONG_AREA = "infrastructureascodewithterraformandkubernetes";

export const mentoringShell: Record<string, unknown> = {
  ...shellOfCommunity,
  [`/axonia/communities/${SLUG}`]: { ...community, mentoringEnabled: true },
};

export const person = (userId: string, displayName: string) => ({
  userId,
  displayName,
  avatarUrl: null,
  areas: [LONG_AREA, "observability", "kafka"],
  proposable: true,
  proposalState: null,
  proposalsOpenOn: null,
});

export const pairing = {
  id: 11,
  communitySlug: SLUG,
  communityName: community.name,
  myRole: "MENTOR",
  partnerId: "u-2",
  partnerName: "Bartholomew Montgomery-Whitfield",
  partnerGivenName: null,
  partnerAvatarUrl: null,
  startedAt: "2026-09-02T10:00:00Z",
  endedAt: null,
  stops: ["DONE", "IN_PROGRESS", "TODO"],
  nowTopic: "Kafka consumer groups",
  nextMeeting: { on: "2026-11-09", passed: true },
  laterPlanned: 2,
  meetings: 2,
  notes: 3,
  lastMetOn: "2026-11-12",
};

const longLink = {
  id: 5,
  kind: "LINK",
  title: null,
  href: "https://example.test/a/very/long/address/that/keeps/going/on",
  available: true,
};

const topic = (id: number, label: string, state: string, position = 0) => ({
  id,
  label,
  area: LONG_AREA,
  state,
  position,
  pointers: id === 1 ? [longLink] : [],
});

const note = (
  id: number,
  mine: boolean,
  meetingOn: string,
  topicIds: number[],
) => ({
  id,
  authorId: mine ? E2E_USER_ID : "u-2",
  authorName: mine ? "Gabriel D. (Vader)" : "Bartholomew Montgomery-Whitfield",
  authorAvatarUrl: null,
  mine,
  body: "First hour: how to say what landed and what did not, with a word long enough to wrap: postmortemfacilitationchecklist.",
  meetingOn,
  topicIds,
  writtenAt: `${meetingOn}T11:00:00Z`,
  editedAt: mine ? `${meetingOn}T12:00:00Z` : null,
  pointers: mine ? [longLink] : [],
});

const planned = (on: string, index: number) => ({
  id: index + 1,
  on,
  topicIds: [1, 2, 3],
  plannedById: "u-2",
  plannedByName: "Bartholomew Montgomery-Whitfield",
  passed: index === 0,
});

export const journal = {
  pairing,
  writable: true,
  today: "2026-11-16",
  startedOn: "2026-09-02",
  topics: [
    topic(
      1,
      "Running a blameless postmortem after a production incident",
      "DONE",
    ),
    topic(2, "Kafka consumer groups", "IN_PROGRESS"),
    topic(3, "Writing an RFC", "TODO"),
    topic(4, "Talking to stakeholders", "TODO", 1),
  ],
  notes: [
    note(2, true, "2026-10-08", [1]),
    note(3, false, "2026-10-08", [1, 2]),
    note(4, true, "2026-11-12", [2]),
  ],
  meetings: ["2026-11-09", "2026-11-19", "2026-12-02"].map(planned),
  wordsWritten: false,
  canWriteWords: false,
  myWords: null,
  showing: { visibility: "ASKING", iSaidYes: false },
};
