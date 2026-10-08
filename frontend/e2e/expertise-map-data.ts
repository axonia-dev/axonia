import type { Page } from "@playwright/test";

import { stubShell } from "./api";
import { signedIn } from "./session";

/** The payload the map draws in these specs: twelve areas, and contributions of all four kinds across them. */
export const SLUG = "backend-guild";

export function topic(tag: string, signals: number, holder: string) {
  return {
    canonicalTag: tag,
    practitionerCount: 4,
    signalCount: signals,
    topHolderShare: 0.3,
    topHolderName: holder,
    presentedCount: Math.round(signals / 3),
    contributedCount: Math.round(signals / 2),
    offeredCount: 2,
    lastActivityAt: "2026-09-01T10:00:00Z",
    openToQuestionsCount: 1,
  };
}

export const TAGS = [
  "kafka",
  "postgres",
  "kubernetes",
  "terraform",
  "graphql",
  "rust",
  "observability",
  "testing",
  "security",
  "caching",
  "streaming",
  "scaling",
];

export const topics = {
  topics: TAGS.map((tag, index) => topic(tag, 30 - index, "Ada Lovelace")),
  links: TAGS.slice(1).map((tag) => ({ a: TAGS[0], b: tag, weight: 2 })),
  namedAreas: [],
};

export const KINDS = ["SLOT", "ARTIFACT", "TOPIC", "ACTION_ITEM"] as const;
export const SIGNALS = [
  "PRESENTED",
  "CONTRIBUTED",
  "OFFERED",
  "COMPLETED",
] as const;

/**
 * The day a contribution landed on, over a window of eight months. Deliberately not one a day: a community that
 * contributes at a perfectly even rate draws the stream as a rectangle, which proves the binning honest and shows
 * nothing about the reading. This swells and subsides twice, which is what a practice actually does.
 */
function dayOf(index: number, count: number): number {
  const t = index / Math.max(1, count - 1);
  return Math.round(239 * (t - 0.12 * Math.sin(2 * Math.PI * t)));
}

export function contributions(count: number) {
  return {
    people: [
      { userId: "u1", displayName: "Ada Lovelace", avatarUrl: null },
      { userId: "u2", displayName: "Grace Hopper", avatarUrl: null },
    ],
    items: Array.from({ length: count }, (_, i) => ({
      signalId: i + 1,
      canonicalTag: TAGS[i % TAGS.length],
      userId: i % 2 === 0 ? "u1" : "u2",
      // A spread of attendance, so the face gate has a real distribution to take a percentile of
      // rather than one number repeated.
      reach: i % 4 === 0 ? 10 + (i % 90) : null,
      evidence: {
        signalType: SIGNALS[i % 4],
        sourceType: KINDS[i % 4],
        sourceId: 100 + i,
        label: `Talk number ${i + 1}`,
        communitySlug: SLUG,
        occurredAt: new Date(
          Date.UTC(2026, 0, 1 + dayOf(i, count)),
        ).toISOString(),
        sessionId: 500 + i,
        typeId: null,
        typeLabel: null,
        typeIcon: null,
        typeAccentColor: null,
      },
    })),
    truncated: false,
  };
}

export const community = {
  id: 1,
  slug: SLUG,
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "We backend",
  status: "ACTIVE",
  memberCount: 12,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

export const member = {
  id: 9,
  communityId: 1,
  userId: "dev-user",
  role: "MEMBER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

export const answers: { at: (path: string) => boolean; body: unknown }[] = [
  {
    at: (p) => p.endsWith("/expertise/map/contributions"),
    body: contributions(240),
  },
  { at: (p) => p.endsWith("/expertise/map"), body: topics },
  { at: (p) => p.endsWith(`/communities/${SLUG}/members/me`), body: member },
  { at: (p) => p.endsWith(`/communities/${SLUG}`), body: community },
  { at: (p) => p.endsWith("/management-policy"), body: {} },
  { at: (p) => p.endsWith("/me/onboarding"), body: { completed: true } },
  {
    at: (p) => p.endsWith("/communities"),
    body: {
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    },
  },
];

export const unstubbed = new Set<string>();

/** A signed-in reader with the payload above answering for the map. Both map specs open the page through it. */
export async function stubbedMap({ page }: { page: Page }): Promise<void> {
  await signedIn(page);
  // Registered before stubShell so its catch-all falls back to these rather than swallowing them.
  await page.route("**/axonia/**", (route) => {
    const { pathname } = new URL(route.request().url());
    const answer = answers.find((a) => a.at(pathname));
    if (answer === undefined) {
      unstubbed.add(pathname);
      return route.fulfill({ status: 404, json: {} });
    }
    return route.fulfill({ json: answer.body });
  });
  await stubShell(page);
}

/** The first mark, by its accessible name, without also matching "Talk number 10" and its hundred siblings. */
export const FIRST_MARK = /^Talk number 1, /;
