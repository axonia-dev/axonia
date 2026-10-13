import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { E2E_USER_ID, stubShell } from "./api";
import { overflowing } from "./overflow";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";
const SLUG = "backend-guild";

// A phone, emulated as a phone. This is the one width the rest of the suite cannot speak for:
// a desktop Chrome window does not go below roughly 500px, so a row that only bursts its
// container under that is invisible both to a developer dragging the window and to every other
// project in this config. jsdom cannot see it either - it has no layout - so this file is the
// only gate that measures a real box at a real phone width.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

function paged<T>(content: T[]) {
  return {
    content,
    page: {
      totalElements: content.length,
      totalPages: 1,
      number: 0,
      size: 50,
    },
  };
}

const community = {
  id: 1,
  slug: SLUG,
  name: "La communauté de l'anneau",
  kindId: 1,
  kind: "Guild",
  description: "We backend",
  status: "ACTIVE",
  memberCount: 3,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const member = {
  id: 9,
  communityId: 1,
  userId: E2E_USER_ID,
  role: "ORGANIZER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const contributor = {
  id: E2E_USER_ID,
  displayName: "Gabriel D. (Vader)",
  email: "dev@example.test",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
};

/** The provenance line as the library renders it: the series, the topic it was captured from,
 *  and the date. The card joins those three itself when it can resolve the session, so handing
 *  the whole line over as the snapshot label is the short way to put a realistic one on screen -
 *  what the layout has to survive is the length, not where the words came from. */
const PROVENANCE =
  "Monthly Backend Guild · I'd like to know more about Spring Boot (28 Aug 2026)";

const artifact = {
  id: 31,
  communityId: 1,
  typeId: 1,
  type: "ADR",
  title: "I'd like to know more about Spring Boot",
  description: "",
  url: "https://example.com/spring",
  tags: ["spring"],
  contributorUserId: E2E_USER_ID,
  loveCount: 0,
  provenance: { sessionId: 4, segmentId: 8, label: PROVENANCE },
  status: "ACTIVE",
  createdAt: "2026-08-28T10:00:00Z",
  updatedAt: "2026-08-28T10:00:00Z",
};

const topic = {
  id: 5,
  communityId: 1,
  kind: "OFFER",
  title: "I know Kafka!",
  description: "",
  tags: [],
  proposerUserId: E2E_USER_ID,
  proposerDisplayName: contributor.displayName,
  canClose: false,
  canReopen: false,
  voteCount: 0,
  hasVoted: false,
  canEdit: true,
  canDelete: true,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "2026-08-28T10:00:00Z",
  updatedAt: "2026-08-28T10:00:00Z",
};

/** The community shell's own reads, shared by both pages under test. */
const shellOfCommunity: Record<string, unknown> = {
  [`/axonia/communities/${SLUG}`]: community,
  [`/axonia/communities/${SLUG}/members/me`]: member,
  [`/axonia/communities/${SLUG}/management-policy`]: {},
  "/axonia/communities": paged([community]),
  "/axonia/me/onboarding": { state: "COMPLETED" },
};

/** Answers keyed by exact pathname, so `/artifacts` and `/artifacts/stats` cannot shadow each
 *  other the way a prefix table lets them. Anything unlisted is a 404, which every read here
 *  degrades to an empty surface rather than a broken one. */
async function stubReads(
  page: Page,
  answers: Record<string, unknown>,
): Promise<void> {
  await page.route(apiGlob, (route) => {
    const { pathname } = new URL(route.request().url());
    const body = answers[pathname];
    return route.fulfill({
      status: body === undefined ? 404 : 200,
      contentType: "application/json",
      body: JSON.stringify(body ?? {}),
    });
  });
  await signedIn(page);
  await stubShell(page);
}

test("the library fits a phone, however long an artifact's provenance", async ({
  page: browser,
}) => {
  // given - one artifact, captured from a session whose provenance line is a long one
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/artifacts`]: paged([artifact]),
    [`/axonia/communities/${SLUG}/artifacts/stats`]: {
      total: 1,
      sessionCount: 1,
      capturedThisMonth: 0,
    },
    [`/axonia/communities/${SLUG}/artifacts/sessions`]: [],
    [`/axonia/communities/${SLUG}/sessions/past`]: paged([]),
    [`/axonia/communities/${SLUG}/series`]: paged([]),
    "/axonia/artifact-types": [
      {
        id: 1,
        label: "ADR",
        icon: "file-check",
        accentColor: "#6e3ceb",
        artifactCount: 1,
      },
    ],
    "/axonia/users": [contributor],
  });

  // when - the library opens on a phone
  await browser.goto(`/communities/${SLUG}/library`);

  // then - the card is on screen, and nothing on the page reaches past its right edge.
  // The visibility check comes first on purpose: an empty page cannot overflow, so without
  // it this test would pass by measuring nothing.
  await expect(browser.getByText(PROVENANCE)).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

test("the backlog fits a phone, tab counts and the add control included", async ({
  page: browser,
}) => {
  // given - a backlog with all four view tabs carrying a count
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/topics`]: paged([topic]),
    [`/axonia/communities/${SLUG}/topics/counts`]: {
      proposed: 1,
      scheduled: 0,
      done: 1,
    },
    [`/axonia/communities/${SLUG}/action-items`]: paged([]),
  });

  // when - the backlog opens on a phone
  await browser.goto(`/communities/${SLUG}/backlog`);

  // then - the control that proposes a topic is reachable without scrolling sideways,
  // which is what an overflowing tab row takes away
  await expect(
    browser.getByRole("button", { name: "+ Add new" }),
  ).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

const LONG_AREA = "infrastructureascodewithterraformandkubernetes";

test("the learning page fits a phone, however long an area's name", async ({
  page: browser,
}) => {
  // given - a member holding a long area, which also heads the wanted table and is picked
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/learning`]: {
      member: true,
      archived: false,
      limit: 10,
      mine: [
        { tag: LONG_AREA, declaredAt: "2026-03-02T09:00:00Z" },
        { tag: "observability", declaredAt: "2026-09-12T09:00:00Z" },
      ],
      wanted: [
        { tag: LONG_AREA, learners: 12, canBeAsked: 1 },
        { tag: "observability", learners: 7, canBeAsked: 0 },
      ],
    },
    [`/axonia/communities/${SLUG}/learning/areas/${LONG_AREA}/learners`]: [
      {
        userId: E2E_USER_ID,
        displayName: contributor.displayName,
        avatarUrl: null,
        declaredAt: "2026-03-02T09:00:00Z",
      },
    ],
  });

  // when - the learning page opens on a phone with that area picked
  await browser.goto(`/communities/${SLUG}/learning?area=${LONG_AREA}`);

  // then - the learners are on screen, and nothing on the page reaches past its right edge
  await expect(browser.getByText(contributor.displayName)).toBeVisible();
  expect(await overflowing(browser)).toEqual([]);
});

test("the welcome stage fits a phone", async ({ page: browser }) => {
  // given - a welcome handed off to the community home, as a hub join does
  await stubReads(browser, {
    ...shellOfCommunity,
    [`/axonia/communities/${SLUG}/members`]: paged([member]),
  });
  await browser.addInitScript((slug) => {
    sessionStorage.setItem(
      "axonia.ceremony",
      JSON.stringify({ kind: "welcome", slug, at: Date.now() }),
    );
  }, SLUG);

  // when
  await browser.goto(`/communities/${SLUG}`);
  const card = browser.locator("[data-ceremony-card]");
  await expect(card).toBeVisible();

  // then - the staged card sits inside the screen
  const box = await card.boundingBox();
  expect(box?.x ?? -1).toBeGreaterThanOrEqual(0);
  expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(412);
});
