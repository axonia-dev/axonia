// The handful of endpoints the app shell asks for on every page load, whatever route is open:
// who the caller is, which Organization they are standing in, which others they could switch to,
// what they may do, and whether anything is unread. None of them belong to the feature a spec is
// testing, and answering all of them with one shape (the old blanket page-envelope stub) fails
// schema validation and leaves the shell rendering nothing.
//
// ORDER MATTERS. Playwright tries the most recently registered route first, so this must be
// registered AFTER a spec's own route table. It answers the shell's calls and hands everything
// else back with `route.fallback()`, which is what reaches the spec's table underneath.

import { type Page, test } from "@playwright/test";

/** The caller the shell describes, and the id every spec fixture already attributes work to. */
export const E2E_USER_ID = "dev-user";

export interface ShellData {
  me: Record<string, unknown>;
  currentOrganization: Record<string, unknown>;
  myOrganizations: Record<string, unknown>[];
  permissions: string[];
  unreadCount: number;
}

const defaults = (): ShellData => ({
  me: {
    id: E2E_USER_ID,
    displayName: "Dev User",
    email: "dev@example.test",
    memberStatus: "PRESENT",
    avatarIsOwn: false,
  },
  // Enterprise, because that is the shell the specs navigate: KindLanding sends an ENTERPRISE
  // session to the /communities directory.
  currentOrganization: {
    id: "default",
    name: "Axonia",
    kind: "ENTERPRISE",
    memberCount: 1,
    communicationsEnabled: true,
  },
  myOrganizations: [{ id: "default", name: "Axonia", kind: "ENTERPRISE" }],
  // Empty by default: a spec that needs an administrative control on screen says so by asking
  // for the permission it gates on, rather than every spec silently running as an owner.
  permissions: [],
  unreadCount: 0,
});

/** What a project adds for endpoints only its edition's build calls: its config's metadata.shellAnswers. */
function projectAnswers(): Record<string, () => unknown> {
  const answers: unknown = test.info().project.metadata["shellAnswers"];
  if (typeof answers !== "object" || answers === null) return {};
  return Object.fromEntries(
    Object.entries(answers).map(([path, body]: [string, unknown]) => [
      path,
      () => body,
    ]),
  );
}

export async function stubShell(
  page: Page,
  overrides: Partial<ShellData> = {},
): Promise<void> {
  const data = { ...defaults(), ...overrides };

  const answers: Record<string, () => unknown> = {
    ...projectAnswers(),
    "/axonia/me": () => data.me,
    "/axonia/organizations/current": () => data.currentOrganization,
    "/axonia/me/organizations": () => data.myOrganizations,
    "/axonia/access/me": () => ({ permissions: data.permissions }),
    "/axonia/me/notifications/unread-count": () => ({
      count: data.unreadCount,
    }),
  };

  await page.route("**/axonia/**", (route) => {
    const { pathname } = new URL(route.request().url());
    const answer =
      route.request().method() === "GET" ? answers[pathname] : undefined;
    if (!answer) return route.fallback();
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(answer()),
    });
  });
}
