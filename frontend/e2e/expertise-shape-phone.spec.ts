import type { Page } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { E2E_USER_ID, stubShell } from "./api";
import { overflowing } from "./overflow";
import { signedIn } from "./session";

// A phone, emulated as a phone - see mobile-layout.spec.ts for why this is the one width that matters.
test.use({
  viewport: { width: 412, height: 892 },
  isMobile: true,
  hasTouch: true,
});

const SLUG = "platform-guild";
const LONG_AREA = "Developer Experience and Platform Tooling";
const UNPLACED = "quantum-computing";
const LONG_UNPLACED = "infrastructure-as-code-with-terraform-modules";

const community = {
  id: 1,
  slug: SLUG,
  name: "Platform Guild",
  kindId: 1,
  kind: "Guild",
  description: "We build platforms",
  status: "ACTIVE",
  memberCount: 20,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const member = {
  id: 9,
  communityId: 1,
  userId: E2E_USER_ID,
  role: "MEMBER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function evidence() {
  return {
    presentedCount: 0,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
  };
}

function topicNode(tag: string) {
  return {
    canonicalTag: tag,
    practitionerCount: 3,
    signalCount: 6,
    topHolderShare: 0.3,
    topHolderName: "Ada Lovelace",
    presentedCount: 2,
    contributedCount: 3,
    offeredCount: 1,
    lastActivityAt: "2026-09-01T10:00:00Z",
    openToQuestionsCount: 1,
  };
}

const TAGS = [
  "typescript",
  "eslint",
  "postgres",
  "graphql",
  "rust",
  "observability",
  "security",
  "kubernetes",
  "helm",
  "istio",
  "docker",
  "wasm",
];

const map = {
  topics: TAGS.map(topicNode),
  links: [
    { a: "kubernetes", b: "helm", weight: 3 },
    { a: "kubernetes", b: "istio", weight: 3 },
    { a: "kubernetes", b: "docker", weight: 3 },
  ],
  namedAreas: [
    { id: 1, name: LONG_AREA, tags: ["typescript", "eslint"] },
    { id: 2, name: "Data Stores", tags: ["postgres"] },
    { id: 3, name: "API Design", tags: ["graphql"] },
    { id: 4, name: "Systems Programming", tags: ["rust"] },
    { id: 5, name: "Observability and SRE", tags: ["observability"] },
    { id: 6, name: "Security Practices", tags: ["security"] },
  ],
};

function selfArea(
  tag: string,
  tier: "PRACTITIONER" | "CONTRIBUTOR" | "INTERESTED",
) {
  return {
    canonicalTag: tag,
    tier,
    hidden: false,
    communities: [{ id: community.id, slug: SLUG, name: community.name }],
    evidence: evidence(),
    communityTiers: [{ communityId: community.id, tier }],
  };
}

const selfView = {
  optedOut: false,
  areas: [
    selfArea("typescript", "PRACTITIONER"),
    selfArea("postgres", "CONTRIBUTOR"),
    selfArea("kubernetes", "INTERESTED"),
  ],
  declarations: [
    {
      tag: "security",
      communityId: community.id,
      communityName: community.name,
      communitySlug: SLUG,
      hidden: false,
      declaredAt: "2026-08-01T10:00:00Z",
    },
  ],
  declarableCommunities: [],
};

const learning = {
  member: true,
  archived: false,
  limit: 10,
  mine: [
    { tag: "docker", declaredAt: "2026-08-10T10:00:00Z" },
    { tag: UNPLACED, declaredAt: "2026-08-11T10:00:00Z" },
    { tag: LONG_UNPLACED, declaredAt: "2026-08-12T10:00:00Z" },
  ],
  wanted: [],
};

function paged<T>(content: T[]) {
  return {
    content,
    page: { totalElements: content.length, totalPages: 1, number: 0, size: 50 },
  };
}

async function stubReads(page: Page): Promise<void> {
  const answers: Record<string, unknown> = {
    [`/axonia/communities/${SLUG}/expertise/me`]: selfView,
    [`/axonia/communities/${SLUG}/expertise/map`]: map,
    [`/axonia/communities/${SLUG}/learning`]: learning,
    [`/axonia/communities/${SLUG}`]: community,
    [`/axonia/communities/${SLUG}/members/me`]: member,
    [`/axonia/communities/${SLUG}/management-policy`]: {},
    "/axonia/communities": paged([community]),
    "/axonia/me/onboarding": { state: "COMPLETED" },
  };
  await page.route("**/axonia/**", (route) => {
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

/** SVG <text> elements (axis names, ring labels) whose box reaches past the shape card's own box. */
function clippedText(page: Page): Promise<string[]> {
  return page.evaluate((title) => {
    const heading = [...document.querySelectorAll("h2")].find((h) =>
      h.textContent?.startsWith(title),
    );
    const section = heading?.closest("section");
    if (!section) return ["<shape card not found>"];
    const box = section.getBoundingClientRect();
    return [...section.querySelectorAll("svg text")]
      .filter((text) => {
        const r = text.getBoundingClientRect();
        return (
          r.left < box.left - 0.5 ||
          r.right > box.right + 0.5 ||
          r.top < box.top - 0.5 ||
          r.bottom > box.bottom + 0.5
        );
      })
      .map((text) => text.textContent ?? "");
  }, "Shape in");
}

test.beforeEach(({ page }) => stubReads(page));

test("the shape card fits a phone, long names included", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/me/expertise`);

  const card = page.getByRole("region", { name: `Shape in ${community.name}` });
  await expect(card).toBeVisible();
  const svgBox = await card.locator("svg").first().boundingBox();
  expect(svgBox?.width).toBeGreaterThan(0);

  // The sr-only spoken list is the axes' accessible form; the drawing itself is aria-hidden.
  const spokenLines = await card.locator("li").allTextContents();
  expect(spokenLines.some((l) => l.startsWith(LONG_AREA))).toBe(true);
  expect(spokenLines.some((l) => l.startsWith("around kubernetes"))).toBe(true);
  await expect(
    card.getByText(
      `Want to learn, not on the map: ${LONG_UNPLACED}, ${UNPLACED}`,
    ),
  ).toBeVisible();

  expect(await overflowing(page)).toEqual([]);
});

/** Each unplaced tag's box, and how many lines it takes. */
function unplacedTags(page: Page) {
  return page.evaluate(() => {
    const note = [...document.querySelectorAll("p")].find((p) =>
      p.textContent?.startsWith("Want to learn, not on the map:"),
    );
    const card = note?.closest("section")?.getBoundingClientRect();
    return [...(note?.querySelectorAll("span") ?? [])].map((span) => {
      const box = span.getBoundingClientRect();
      const line = Number.parseFloat(getComputedStyle(span).lineHeight);
      return {
        tag: (span.textContent ?? "").replace(/,$/u, ""),
        lines: Math.round(box.height / line),
        inside:
          card !== undefined &&
          box.left >= card.left - 0.5 &&
          box.right <= card.right + 0.5,
      };
    });
  });
}

test.describe("on a 320px phone", () => {
  test.use({ viewport: { width: 320, height: 800 } });

  test("an unplaced tag stays whole where it fits and breaks only when wider than the card", async ({
    page,
  }) => {
    await page.goto(`/communities/${SLUG}/me/expertise`);
    await expect(
      page.getByRole("region", { name: `Shape in ${community.name}` }),
    ).toBeVisible();

    expect(await unplacedTags(page)).toEqual([
      { tag: LONG_UNPLACED, lines: 2, inside: true },
      { tag: UNPLACED, lines: 1, inside: true },
    ]);
    expect(await overflowing(page)).toEqual([]);
  });
});

test("no axis name is clipped by the shape card", async ({ page }) => {
  await page.goto(`/communities/${SLUG}/me/expertise`);

  await expect(
    page.getByRole("region", { name: `Shape in ${community.name}` }),
  ).toBeVisible();
  expect(await clippedText(page)).toEqual([]);
});
