import { expect, test } from "@playwright/test";

import { SLUG, stubReads } from "./community-home";
import {
  journal,
  LONG_AREA,
  mentoringShell,
  pairing,
  person,
} from "./mentoring-phone-fixtures";
import { overflowing } from "./overflow";

// A phone, emulated as a phone - see mobile-layout.spec.ts for why this is the one width that matters.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

test("the Mentoring page fits a phone, long areas, the limit and long pairings included", async ({
  page: browser,
}) => {
  // given - a mentor taking mentees with a limit, people on both sides, and pairings with long names
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
      pairings: [
        {
          mentor: {
            userId: "u-2",
            displayName: "Bartholomew Montgomery-Whitfield",
            avatarUrl: null,
          },
          mentee: {
            userId: "u-4",
            displayName: "Maximiliana Konstantinopoulou-Abernathy",
            avatarUrl: null,
          },
          startedOn: "2025-09-02",
        },
        {
          mentor: { userId: "u-5", displayName: "Ana", avatarUrl: null },
          mentee: { userId: "u-3", displayName: "Ada", avatarUrl: null },
          startedOn: "2026-10-06",
        },
      ],
    },
  });

  // when
  await browser.goto(`/communities/${SLUG}/mentoring`);

  // then
  await expect(
    browser.getByRole("button", {
      name: "Ask for mentoring from Bartholomew Montgomery-Whitfield",
    }),
  ).toBeVisible();
  await expect(
    browser.getByRole("button", { name: "Offer mentoring to Ada" }),
  ).toBeVisible();
  await expect(
    browser.getByText("Maximiliana Konstantinopoulou-Abernathy"),
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
  // given - an ongoing journal with long topics, long links, notes on two meeting days, and the ask
  await showJournal(browser);

  // when
  await browser.goto(`/communities/${SLUG}/me/mentoring/11`);

  // then
  await expect(browser.getByRole("button", { name: "Show it" })).toBeVisible();
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
  pairsShown: true,
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
