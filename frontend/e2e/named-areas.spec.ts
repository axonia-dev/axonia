import type { Page, Route } from "@playwright/test";
import { expect, test } from "@playwright/test";

import { stubShell } from "./api";
import { answers, contributions, SLUG, topic } from "./expertise-map-data";
import { signedIn } from "./session";

/**
 * Three clusters of six, linked only inside themselves, so detection finds three neighbourhoods. The shared map
 * fixture is one star of twelve, which detection calls a single blob and draws no neighbourhood for at all, and
 * taking two tags out of it would drop it below the detection floor besides.
 */
const CLUSTERS = [
  ["kafka", "streaming", "flink", "pulsar", "nats", "rabbitmq"],
  ["postgres", "mysql", "redis", "caching", "sql", "indexing"],
  ["kubernetes", "terraform", "helm", "docker", "observability", "security"],
];
const TAGS = CLUSTERS.flat();

const NAME = "Event Pipelines";
const PLACED = ["kafka", "streaming"];

interface NamedArea {
  id: number;
  name: string;
  tags: string[];
}

/** Kafka holds its cluster together, so before any placement its neighbourhood is drawn as "around kafka". */
function weightOf(a: string, b: string): number {
  return a === "kafka" || b === "kafka" ? 5 : 3;
}

function pairsOf(cluster: string[]) {
  return cluster.flatMap((a, i) =>
    cluster.slice(i + 1).map((b) => ({ a, b, weight: weightOf(a, b) })),
  );
}

/** Every third contribution lands on a placed tag, so the named area's headline outweighs any it could collide with. */
function tagOf(index: number): string {
  if (index % 3 === 0) return PLACED[index % 2];
  return TAGS[index % TAGS.length];
}

function clusteredContributions() {
  const base = contributions(270);
  return {
    ...base,
    items: base.items.map((item, index) => ({
      ...item,
      canonicalTag: tagOf(index),
    })),
  };
}

/** The backend as far as this flow reaches it: what a create or a placement writes, every later read returns. */
function curationBackend() {
  const namedAreas: NamedArea[] = [];
  const map = () => ({
    topics: TAGS.map((tag, index) => topic(tag, 40 - index, "Ada Lovelace")),
    links: CLUSTERS.flatMap(pairsOf),
    namedAreas,
  });
  const base = `/axonia/communities/${SLUG}/expertise`;
  const namedAreasPath = `${base}/curation/named-areas`;

  function answer(route: Route, pathname: string) {
    const method = route.request().method();
    if (pathname === namedAreasPath && method === "POST") {
      const { name } = route.request().postDataJSON() as { name: string };
      const created = { id: namedAreas.length + 1, name, tags: [] };
      namedAreas.push(created);
      return route.fulfill({ status: 201, json: created });
    }
    const target = namedAreas.find(
      (namedArea) => pathname === `${namedAreasPath}/${namedArea.id}/tags`,
    );
    if (target !== undefined && method === "POST") {
      const { tag } = route.request().postDataJSON() as { tag: string };
      for (const namedArea of namedAreas) {
        namedArea.tags = namedArea.tags.filter((held) => held !== tag);
      }
      target.tags.push(tag);
      return route.fulfill({ status: 204 });
    }
    const reads = new Map<string, () => unknown>([
      [namedAreasPath, () => namedAreas],
      [`${base}/curation/suggestions`, () => []],
      [
        `${base}/curation/vocabulary`,
        () =>
          TAGS.map((tag) => ({
            canonicalTag: tag,
            variants: [],
            peopleCount: 4,
          })),
      ],
      [`${base}/map`, map],
      [`${base}/map/contributions`, clusteredContributions],
    ]);
    const read = method === "GET" ? reads.get(pathname) : undefined;
    if (read !== undefined) return route.fulfill({ json: read() });
    return null;
  }

  return { namedAreas, answer };
}

const unanswered: string[] = [];

async function stubbedCuration(page: Page) {
  const backend = curationBackend();
  await signedIn(page);
  await page.route("**/axonia/**", (route) => {
    const { pathname } = new URL(route.request().url());
    const handled = backend.answer(route, pathname);
    if (handled !== null) return handled;
    const shared = answers.find((a) => a.at(pathname));
    if (shared !== undefined) return route.fulfill({ json: shared.body });
    unanswered.push(`${route.request().method()} ${pathname}`);
    return route.fulfill({ status: 404, json: {} });
  });
  await stubShell(page);
  return backend;
}

async function placeTag(page: Page, tag: string) {
  const card = page.getByRole("region", { name: NAME });
  await card.getByRole("combobox").fill(tag);
  await page.getByRole("option", { name: tag, exact: true }).click();
  await expect(
    card.getByRole("listitem").filter({ hasText: tag }),
  ).toBeVisible();
}

test("a named area a leader creates and fills is drawn under its own name", async ({
  page,
}) => {
  unanswered.length = 0;
  const backend = await stubbedCuration(page);

  await page.goto(`/communities/${SLUG}/expertise/curation`);
  await page.getByRole("button", { name: /new named area/i }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("textbox").fill(NAME);
  await dialog.getByRole("button", { name: "Create" }).click();
  await expect(page.getByRole("region", { name: NAME })).toBeVisible();

  for (const tag of PLACED) await placeTag(page, tag);
  expect(backend.namedAreas).toEqual([{ id: 1, name: NAME, tags: PLACED }]);

  await page.goto(`/communities/${SLUG}/expertise?view=map`);
  await expect(page.locator("canvas").first()).toBeVisible();
  // At this box size the globe opens with the named area on its limb, where no name is drawn by design; the flat
  // reading holds the whole map.
  await page.getByRole("radio", { name: "Flat" }).click();

  const named = page.locator('[data-testid="map-label"][data-kind="named"]');
  await expect(named).toHaveCount(1);
  await expect(named).toHaveText(NAME);

  const neighbourhoods = page.locator(
    '[data-testid="map-label"][data-kind="neighbourhood"]',
  );
  await expect(neighbourhoods.first()).toBeVisible();
  const headlines = await neighbourhoods.allTextContents();
  for (const tag of PLACED) {
    expect(headlines.join(" ").toLowerCase()).not.toContain(`around ${tag}`);
  }

  expect(unanswered).toEqual([]);
});
