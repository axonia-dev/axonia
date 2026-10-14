import type { Route } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { stubShell } from "./api";
import { signedIn } from "./session";

const apiGlob = "**/axonia/**";

function community(slug: string, name: string, memberCount: number) {
  return {
    id: slug.length,
    slug,
    name,
    kindId: 1,
    kind: "Guild",
    description: `${name} description`,
    status: "ACTIVE",
    memberCount,
    createdAt: "2026-05-31T10:00:00Z",
    updatedAt: "2026-05-31T10:00:00Z",
  };
}

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

/** A route answering "there is no such resource", which for `members/me` means "not a member". */
const NOT_A_MEMBER = Symbol("not a member");

function notFound(route: Route) {
  return route.fulfill({
    status: 404,
    contentType: "application/json",
    body: "{}",
  });
}

test("create a community, then join and leave another", async ({
  page: browser,
}) => {
  // given — a mocked backend (no real server). design-circle already has its own
  // organizer, so dev-user can join and leave it without hitting the
  // last-organizer rule. An E2E journey is intentionally several when/then pairs.
  let designMembers = [
    {
      id: 1,
      communityId: 2,
      userId: "carol",
      role: "ORGANIZER",
      joinedAt: "x",
      updatedAt: "x",
    },
  ];

  // A small route table keyed by "METHOD path-suffix" keeps the handler flat
  // (one lookup) instead of a long if/else chain.
  const organizer = {
    id: 9,
    communityId: 1,
    userId: "dev-user",
    role: "ORGANIZER",
    joinedAt: "x",
    updatedAt: "x",
  };

  const routes: Record<string, () => unknown> = {
    // The Kind field is a Select fed by this endpoint. Its path
    // ("community-kinds") doesn't contain "/communities", so the suffix
    // extraction below leaves the full pathname as the key.
    "GET /axonia/community-kinds": () => [
      { id: 1, label: "Guild", communityCount: 0 },
    ],
    "GET /communities": () =>
      page([{ ...community("design-circle", "Design Circle", 1), role: null }]),
    "POST /communities": () => community("backend-guild", "Backend Guild", 1),
    "GET /communities/backend-guild": () =>
      community("backend-guild", "Backend Guild", 1),
    "GET /communities/design-circle": () =>
      community("design-circle", "Design Circle", designMembers.length),
    "GET /communities/backend-guild/members": () => page([organizer]),
    "GET /communities/design-circle/members": () => page(designMembers),
    // Whether the caller is a member is its own resource now, and it is what the Join/Leave
    // action reads - not the member list. A non-member has no membership, and 404 is the
    // answer to that rather than an error.
    "GET /communities/backend-guild/members/me": () => organizer,
    "GET /communities/design-circle/members/me": () =>
      designMembers.find((m) => m.userId === "dev-user") ?? NOT_A_MEMBER,
    "POST /communities/design-circle/members": () => {
      designMembers = [
        ...designMembers,
        {
          id: 2,
          communityId: 2,
          userId: "dev-user",
          role: "MEMBER",
          joinedAt: "x",
          updatedAt: "x",
        },
      ];
      return designMembers[1];
    },
    "DELETE /communities/design-circle/members/me": () => {
      designMembers = designMembers.filter((m) => m.userId !== "dev-user");
      return undefined;
    },
  };

  const createdStatus = new Set([
    "POST /communities",
    "POST /communities/design-circle/members",
  ]);

  await browser.route(apiGlob, async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const suffix = url.pathname.replace(/^.*(\/communities)/, "$1");
    const key = `${method} ${suffix}`;
    const handler = routes[key];

    if (!handler) {
      return notFound(route);
    }

    const body = handler();
    if (body === NOT_A_MEMBER) {
      return notFound(route);
    }
    if (body === undefined) {
      return route.fulfill({ status: 204, body: "" });
    }
    return route.fulfill({
      status: createdStatus.has(key) ? 201 : 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });

  // The session and the shell's own calls. Registered after the feature route table above:
  // Playwright tries the most recently registered route first, so these answer the shell and
  // hand everything else back to the table underneath.
  await signedIn(browser);
  await stubShell(browser);

  // when — create a community / then — it lands on the new community's detail
  await browser.goto("/communities/new");
  await browser.getByLabel("Name").fill("Backend Guild");
  // Kind is a Select: open it and choose the option, rather than typing.
  await browser.getByLabel("Kind").click();
  await browser.getByRole("option", { name: "Guild" }).click();
  await browser.getByRole("button", { name: "Create" }).click();

  // then - the birth plays over the page, then settles into the hero
  const stage = browser.locator("[data-ceremony-stage]");
  await expect(stage).toBeVisible();
  await expect(stage).toBeHidden({ timeout: 10_000 });
  await expect(
    browser.getByRole("heading", { name: "Backend Guild" }),
  ).toBeVisible();

  // when — open a community dev-user can join / then — it shows Join
  await browser.goto("/communities/design-circle");
  await expect(
    browser.getByRole("heading", { name: "Design Circle" }),
  ).toBeVisible();

  // when - join / then - the hero drops the Join call and states the standing instead,
  // alongside the menu that only a member gets
  await browser.getByRole("button", { name: "Join" }).click();

  // then - the welcome plays, and any key ends it at once
  await expect(stage).toBeVisible();
  await browser.keyboard.press("Escape");
  await expect(stage).toBeHidden();

  // when - the page reloads / then - nothing replays
  await browser.reload();
  await expect(
    browser.getByRole("button", { name: "Community actions" }),
  ).toBeVisible();
  await expect(stage).toHaveCount(0);
  await expect(browser.getByRole("button", { name: "Join" })).toBeHidden();

  // when - leave, which lives in that menu now rather than on the hero. It acts at once
  // here: only a hub asks to confirm, and this Organization is an enterprise.
  await browser.getByRole("button", { name: "Community actions" }).click();
  await browser.getByRole("menuitem", { name: "Leave community" }).click();

  // then - the front door offers Join again
  await expect(browser.getByRole("button", { name: "Join" })).toBeVisible();

  // when - join again with reduced motion asked for / then - no stage, straight to member
  await browser.emulateMedia({ reducedMotion: "reduce" });
  await browser.getByRole("button", { name: "Join" }).click();
  await expect(
    browser.getByRole("button", { name: "Community actions" }),
  ).toBeVisible();
  await expect(stage).toHaveCount(0);
});
