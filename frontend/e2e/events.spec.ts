import { expect, test } from "@playwright/test";

import { stubShell } from "./api";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";

// The envelope Spring's PagedModel sends: the counts sit under `page`, not beside
// `content`. Every list schema in the app is built from `pageSchema`, which parses
// exactly this shape and rejects the flat one.
function page<T>(content: T[]) {
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
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "We backend",
  status: "ACTIVE",
  memberCount: 1,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const organizer = {
  id: 9,
  communityId: 1,
  userId: "dev-user",
  role: "ORGANIZER",
  joinedAt: "x",
  updatedAt: "x",
};

// This spec used to carry the rest of the journey too: claim the open slot on an
// upcoming session, then signal going. Both halves were driven through a "Claim"
// button on the Upcoming view, and that button no longer exists anywhere in the app -
// a session's talks are filled by adding presenters to a section's slots now, which is
// a different interaction rather than a renamed one. Re-authoring that journey against
// the current sessions model is real work with a real design question in it (which
// journey is worth an end-to-end test), so it is written down as its own task rather
// than left here as a spec asserting a screen nobody ships.
test("propose a topic from the community backlog", async ({
  page: browser,
}) => {
  // given - a mocked backend. Proposed topics accumulate in an array so the list
  // read after the write returns what the write put there.
  let topics: unknown[] = [];

  const topic = {
    id: 5,
    communityId: 1,
    kind: "REQUEST",
    title: "Intro to event sourcing",
    description: "please",
    tags: [],
    proposerUserId: "dev-user",
    proposerDisplayName: "Dev User",
    canClose: false,
    canReopen: false,
    voteCount: 0,
    hasVoted: false,
    canEdit: true,
    canDelete: true,
    status: "PROPOSED",
    scheduledFor: null,
    createdAt: "x",
    updatedAt: "x",
  };

  // A data-driven route table (first matching predicate wins) keeps the handler
  // body flat - the branching lives in data, not control flow.
  const ok = (body: unknown) => ({ body, status: 200 });
  const created = (body: unknown) => ({ body, status: 201 });

  interface Route {
    method: string;
    test: (path: string) => boolean;
    handle: () => { body: unknown; status: number };
  }

  const routes: Route[] = [
    {
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild"),
      handle: () => ok(community),
    },
    {
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild/members/me"),
      handle: () => ok(organizer),
    },
    {
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild/members"),
      handle: () => ok(page([organizer])),
    },
    {
      // The backlog's view chips read their numbers from here, not from the list.
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild/topics/counts"),
      handle: () => ok({ proposed: topics.length, scheduled: 0, done: 0 }),
    },
    {
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild/management-policy"),
      handle: () => ok({}),
    },
    {
      method: "GET",
      test: (p) => p.endsWith("/communities/backend-guild/topics"),
      handle: () => ok(page(topics)),
    },
    {
      method: "POST",
      test: (p) => p.endsWith("/communities/backend-guild/topics"),
      handle: () => {
        topics = [topic];
        return created(topic);
      },
    },
  ];

  await browser.route(apiGlob, async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    const match = routes.find((r) => r.method === method && r.test(path));
    if (!match) {
      return route.fulfill({
        status: 404,
        contentType: "application/json",
        body: "{}",
      });
    }
    const { body, status } = match.handle();
    return route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });

  // The session and the shell's own calls. Registered after the feature route table above:
  // Playwright tries the most recently registered route first, so these answer the shell and
  // hand everything else back to the table underneath.
  await signedIn(browser);
  await stubShell(browser);

  // when - open the community and go to the Backlog section via the rail.
  // Scoped to the rail: the shell also renders a mobile tab bar carrying the same
  // section links, so an unscoped role query matches both.
  const rail = browser.getByTestId("community-rail");
  await browser.goto("/communities/backend-guild");
  await rail.getByRole("link", { name: "Backlog" }).click();

  // when - propose a topic, which is a dialog off the backlog now rather than an
  // inline form on the page
  await browser.getByRole("button", { name: "+ Add new" }).click();
  await browser.getByLabel("Title").fill("Intro to event sourcing");
  await browser.getByRole("button", { name: "Propose" }).click();

  // then - it lands in the backlog
  await expect(browser.getByText("Intro to event sourcing")).toBeVisible();
});
