import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test, vi } from "vitest";

import type {
  MapContributions,
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const STREAMING = ["kafka", "debezium", "flink", "connect", "avro", "schema"];
const WEB = ["react", "vite", "css", "typescript", "jest", "eslint"];

/** Only these two have artifacts and no talk, so "Not yet presented" answers with exactly them. */
const UNPRESENTED = ["flink", "vite"];

function node(canonicalTag: string): TopicNode {
  const unpresented = UNPRESENTED.includes(canonicalTag);
  return {
    canonicalTag,
    practitionerCount: 1,
    signalCount: 2,
    topHolderShare: 1,
    topHolderName: "Dana Krupa",
    presentedCount: unpresented ? 0 : 2,
    contributedCount: unpresented ? 1 : 0,
    offeredCount: 0,
    lastActivityAt: "2026-08-01T00:00:00Z",
    openToQuestionsCount: 0,
  };
}

/** A clique per neighbourhood, which is what gives the partition two groups rather than one blob. */
function clique(tags: string[]): TopicLink[] {
  return tags.flatMap((a, index) =>
    tags.slice(index + 1).map((b) => ({ a, b, weight: 3 })),
  );
}

const topics = [...STREAMING, ...WEB].map(node);
const links = [...clique(STREAMING), ...clique(WEB)];

const contributions: MapContributions = {
  people: [{ userId: "u-1", displayName: "Ada Lovelace", avatarUrl: null }],
  items: topics.map((topic, index) => ({
    signalId: index + 1,
    canonicalTag: topic.canonicalTag,
    userId: "u-1",
    reach: null,
    evidence: {
      signalType: "PRESENTED" as const,
      sourceType: "SLOT" as const,
      sourceId: index + 1,
      label: `Scaling ${topic.canonicalTag}`,
      communitySlug: "platform-guild",
      occurredAt: "2026-08-01T00:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
    },
  })),
  truncated: false,
};

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: { topics, links, namedAreas: [] },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/expertise/api/use-map-contributions", () => ({
  useMapContributions: () => ({ data: contributions }),
}));

vi.mock("@/features/expertise/ui/TerrainCanvas", () => ({
  TerrainCanvas: () => <div data-testid="terrain-canvas" aria-hidden="true" />,
}));
vi.mock("@/features/expertise/ui/StreamCanvas", () => ({
  StreamCanvas: () => <div data-testid="stream-canvas" aria-hidden="true" />,
}));

async function renderMap() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ExpertiseMapView />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByTestId("terrain-canvas");
}

/**
 * The flat reading, where every area faces the reader.
 *
 * The globe turns most of a community round its own far side, and which names happen to be in frame there is a fact
 * about the camera rather than about the highlight. With two neighbourhoods the layout settles into a belt at the
 * equator whose ends are the limb, so in jsdom's fallback box the globe names nothing at all. What the globe does
 * turn towards the reader is proved against a real GPU in `e2e/expertise-map-names.spec.ts`, and the facing floor
 * itself in `terrain-camera.test.ts`.
 */
async function flat() {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));
}

/** Every name the drawing found room for, and whether the panel beside it is about that name's ground. */
function litness(): Record<string, string | undefined> {
  return Object.fromEntries(
    screen
      .getAllByTestId("map-label")
      .map((node) => [node.textContent, node.dataset["lit"]]),
  );
}

async function chooseReading(label: string) {
  await user.click(screen.getByRole("button", { name: /reading/i }));
  await user.click(screen.getByRole("option", { name: label }));
}

// Colour alone says a region exists; it cannot say what the region is about, and a reader looking at a handful of
// coloured blobs with no word on any of them is being asked to take the picture on trust.
test("names the areas on the drawing itself, not only in the panel beside it", async () => {
  await flat();
  const areas = Object.keys(litness()).filter((name) =>
    topics.some((topic) => topic.canonicalTag === name),
  );

  expect(areas.length).toBeGreaterThan(0);
});

test("headlines a neighbourhood over the drawing", async () => {
  await flat();

  expect(Object.keys(litness())).toContain("AROUND AVRO");
});

test("carries the names onto the other reading too", async () => {
  await renderMap();

  await user.click(screen.getByRole("radio", { name: "Over time" }));
  await screen.findByTestId("stream-canvas");

  expect(Object.keys(litness()).length).toBeGreaterThan(1);
});

// Changing the reading used to change nothing a reader could see on the map beside it.
test("picks the reading's own areas out of the map", async () => {
  await flat();

  await chooseReading("Not yet presented");
  // Which names the drawing found room for is decided by collision culling, so the areas are read off what it drew
  // rather than named here: the claim is that every one of them agrees with the reading.
  const drawn = Object.entries(litness()).filter(([name]) =>
    topics.some((topic) => topic.canonicalTag === name),
  );

  expect(drawn.length).toBeGreaterThan(0);
  expect(drawn.map(([name, lit]) => [name, lit === "true"])).toStrictEqual(
    drawn.map(([name]) => [name, UNPRESENTED.includes(name)]),
  );
});

test("gives the whole map back when the panel stops pointing at anything", async () => {
  await flat();
  await chooseReading("Not yet presented");
  expect(Object.values(litness())).toContain("false");

  await chooseReading("Neighbourhoods");

  expect(Object.values(litness())).not.toContain("false");
});

test("picks an area chosen in the panel out of the map", async () => {
  await flat();

  await user.click(
    screen.getByRole("button", { name: /^avro, and 5 areas alongside it$/ }),
  );
  const lit = litness();

  expect(lit["AROUND AVRO"]).toBe("true");
  expect(lit["vite"]).toBe("false");
});

/** Finding an area by name in a panel that lists sixty of them is a scroll, and finding one talk among five
 *  thousand marks was not possible at all. */
async function findOnMap(term: string) {
  await user.type(
    screen.getByRole("searchbox", { name: /find on the map/i }),
    term,
  );
}

test("picks the areas a term names out of the map", async () => {
  await flat();

  await findOnMap("flink");
  await screen.findByText(/1 area/);
  const drawn = Object.entries(litness()).filter(([name]) =>
    topics.some((topic) => topic.canonicalTag === name),
  );

  expect(drawn.length).toBeGreaterThan(0);
  expect(drawn.map(([name, lit]) => [name, lit === "true"])).toStrictEqual(
    drawn.map(([name]) => [name, name === "flink"]),
  );
});

test("counts what the term found, areas and contributions apart", async () => {
  await flat();

  await findOnMap("flink");

  expect(await screen.findByText("1 area, 1 contribution")).toBeInTheDocument();
});

/** A drawing dimmed end to end cannot say "nothing matched"; it can only look broken. The words say it instead. */
test("leaves the map alone for a term that found nothing", async () => {
  await flat();

  await findOnMap("prolog");

  expect(await screen.findByText("No matches")).toBeInTheDocument();
  expect(Object.values(litness())).not.toContain("false");
});

test("gives the whole map back when the box is emptied", async () => {
  await flat();
  await findOnMap("flink");
  await screen.findByText(/1 area/);

  await user.click(screen.getByRole("button", { name: /clear search/i }));

  await waitFor(() => expect(screen.queryByText(/1 area/)).toBeNull());
  expect(Object.values(litness())).not.toContain("false");
});

/** The reading and the term are two questions about one map. Intersected, a term typed inside a reading that
 *  lists three areas empties the map under the reader; the term answers instead. */
test("answers with the term rather than with the reading behind it", async () => {
  await flat();
  await chooseReading("Not yet presented");

  await findOnMap("kafka");
  await screen.findByText(/1 area/);
  // Which names the drawing found room for is decided by collision culling, so the areas are read off what it drew.
  const drawn = Object.entries(litness()).filter(([name]) =>
    topics.some((topic) => topic.canonicalTag === name),
  );

  expect(drawn.map(([name, lit]) => [name, lit === "true"])).toStrictEqual(
    drawn.map(([name]) => [name, name === "kafka"]),
  );
});
