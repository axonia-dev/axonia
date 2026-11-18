import { expect, test } from "@playwright/test";

import { E2E_USER_ID } from "./api";
import { community, shellOfCommunity, SLUG, stubReads } from "./community-home";
import { overflowing } from "./overflow";

// A phone, emulated as a phone - see mobile-layout.spec.ts for why this is the one width that matters.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

const LONG_AREA = "infrastructureascodewithterraformandkubernetes";

const mentoringShell: Record<string, unknown> = {
  ...shellOfCommunity,
  [`/axonia/communities/${SLUG}`]: { ...community, mentoringEnabled: true },
};

const person = (userId: string, displayName: string) => ({
  userId,
  displayName,
  avatarUrl: null,
  areas: [LONG_AREA, "observability", "kafka"],
  proposable: true,
  proposalState: null,
  proposalsOpenOn: null,
});

const pairing = {
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

const journal = {
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
};

test("the Mentoring page fits a phone, long areas and the limit included", async ({
  page: browser,
}) => {
  // given - a mentor taking mentees with a limit, and people on both sides
  await stubReads(browser, {
    ...mentoringShell,
    [`/axonia/communities/${SLUG}/mentoring`]: {
      archived: false,
      mine: { takingMentees: true, menteeLimit: 3, lookingForMentor: true },
      mentors: [person("u-2", "Bartholomew Montgomery-Whitfield")],
      mentees: [person("u-3", "Ada")],
      canMentorOn: [LONG_AREA, "observability"],
      wantsToGrowIn: [LONG_AREA, "kafka"],
      guidedStart: false,
    },
  });

  // when
  await browser.goto(`/communities/${SLUG}/mentoring`);

  // then
  await expect(
    browser.getByRole("button", { name: "Propose mentoring" }).first(),
  ).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

function showJournal(browser: Parameters<typeof stubReads>[0]) {
  return stubReads(browser, {
    ...mentoringShell,
    "/axonia/mentoring/pairings/11": journal,
  });
}

test("a journal fits a phone, its trail, board and logbook included", async ({
  page: browser,
}) => {
  // given - an ongoing journal with long topics, long links and notes on two meeting days
  await showJournal(browser);

  // when
  await browser.goto(`/communities/${SLUG}/me/mentoring/11`);

  // then
  await expect(
    browser.getByRole("list", { name: "Trail" }).getByRole("button").first(),
  ).toBeVisible();
  await expect(
    browser.getByRole("region", { name: "In progress" }),
  ).toBeVisible();
  await expect(browser.getByRole("heading", { name: "Logbook" })).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

test("a topic's links fit a phone", async ({ page: browser }) => {
  // given
  await showJournal(browser);
  await browser.goto(`/communities/${SLUG}/me/mentoring/11`);

  // when - a topic opens from its sticker, and a link is about to be added to it
  await browser
    .getByRole("button", { name: /^Running a blameless/ })
    .and(browser.locator("[aria-haspopup]"))
    .click();
  await browser
    .getByRole("button", { name: /^Add link to Running a blameless/ })
    .click();

  // then
  await expect(
    browser.getByRole("textbox", { name: /^Address to link from/ }),
  ).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

test("the logbook's composer fits a phone", async ({ page: browser }) => {
  // given
  await showJournal(browser);
  await browser.goto(`/communities/${SLUG}/me/mentoring/11`);

  // when
  await browser.getByRole("button", { name: "Write a note" }).click();

  // then
  await expect(browser.getByRole("textbox", { name: "Note" })).toBeFocused();
  expect(await overflowing(browser)).toEqual([]);
});

const LONG_COMMUNITY = "Platform Engineering and Developer Experience Guild";

const proposed = (id: number, proposedBy: string, partnerName: string) => ({
  id,
  communitySlug: SLUG,
  communityName: LONG_COMMUNITY,
  proposedBy,
  partnerId: `u-${id}`,
  partnerName,
  partnerAvatarUrl: null,
  topics: [LONG_AREA, "On-call habits"],
  message: "A fortnightly hour, starting in December?",
  status: "OPEN",
  createdAt: "2026-11-12T10:00:00Z",
  reopenableUntil: null,
  lapsesOn: "2026-12-12",
});

const ended = (id: number, startedAt: string, endedAt: string) => ({
  ...pairing,
  id,
  myRole: "MENTEE",
  partnerId: "u-5",
  partnerName: "Alice Anderson",
  startedAt,
  endedAt,
  stops: ["DONE", "DONE"],
  nextMeeting: null,
  laterPlanned: 0,
});

const myMentoring = {
  inAMentoringGuild: true,
  received: [proposed(31, "MENTEE", "Dan Okafor")],
  sent: [proposed(32, "MENTEE", "Priya Nair")],
  ongoing: [
    {
      ...pairing,
      communityName: LONG_COMMUNITY,
      nowTopic: "Running a blameless postmortem after a production incident",
    },
    {
      ...pairing,
      id: 12,
      myRole: "MENTEE",
      partnerId: "u-6",
      partnerName: "Evan Brooks",
      stops: [],
      nowTopic: null,
      nextMeeting: null,
      laterPlanned: 0,
      meetings: 0,
      notes: 0,
      lastMetOn: null,
    },
  ],
  ended: [
    ended(13, "2025-02-01T10:00:00Z", "2025-06-01T10:00:00Z"),
    ended(14, "2026-01-10T10:00:00Z", "2026-05-20T10:00:00Z"),
  ],
  today: "2026-11-17",
};

test("My mentoring fits a phone, a letter, strips and a stacked summit included", async ({
  page: browser,
}) => {
  // given - a letter, a sent slip, a mentor and a mentee, and two ended pairings with one person
  await stubReads(browser, {
    ...mentoringShell,
    "/axonia/mentoring/me": myMentoring,
  });

  // when
  await browser.goto(`/communities/${SLUG}/me/mentoring`);

  // then
  await expect(
    browser.getByRole("button", { name: "Accept as mentor" }),
  ).toBeVisible();
  await expect(
    browser.getByRole("heading", { name: "My mentors" }),
  ).toBeVisible();
  await expect(
    browser.getByRole("heading", { name: "My mentees" }),
  ).toBeVisible();
  await expect(browser.getByText("×2")).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});
