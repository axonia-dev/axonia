import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";

import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

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

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: { topics, links, namedAreas: [] },
    isLoading: false,
    isError: false,
  }),
}));

const EMPTY = vi.hoisted(() => ({
  data: { people: [], items: [], truncated: false },
}));

vi.mock("@/features/expertise/api/use-map-contributions", () => ({
  useMapContributions: () => EMPTY,
}));

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    search: () =>
      Promise.resolve([
        {
          userId: "ada",
          displayName: "Ada",
          avatarUrl: null,
          orgUnit: null,
          tier: "PRACTITIONER",
          evidence: {
            presentedCount: 1,
            contributedCount: 0,
            offeredCount: 0,
            recent: [],
          },
          openToQuestions: false,
          declaredAt: null,
        },
      ]),
  },
}));

vi.mock("@/features/expertise/ui/MapDrawing", () => ({
  MapDrawing: () => <div data-testid="drawing" />,
}));

afterEach(() => {
  vi.restoreAllMocks();
});

/** A window this wide: every `min-width` query up to it matches, and nothing else does. */
function windowOf(width: number) {
  const real = window.matchMedia;
  vi.spyOn(window, "matchMedia").mockImplementation((query) => {
    const least = /min-width: (\d+)px/.exec(query)?.[1];
    return {
      ...real(query),
      matches: least !== undefined && Number(least) <= width,
    };
  });
}

const panel = () =>
  screen.getByRole("complementary", { name: "Topic readings" });

async function renderMap() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ExpertiseMapView />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await screen.findByTestId("drawing");
}

async function search(user: ReturnType<typeof userEvent.setup>, term: string) {
  await user.type(
    screen.getByRole("searchbox", { name: /find on the map/i }),
    term,
  );
  await act(() => new Promise((resolve) => setTimeout(resolve, 260)));
}

test("choosing a matched area inside a reading leaves the reading and opens the area", async () => {
  const user = userEvent.setup({ delay: null });
  await renderMap();
  await user.click(screen.getByRole("button", { name: /reading/i }));
  await user.click(screen.getByRole("option", { name: "Not yet presented" }));

  await search(user, "f");
  await user.click(screen.getByRole("button", { name: "flink" }));

  expect(
    await screen.findByRole("heading", { name: "Who knows flink" }),
  ).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "flink" })).toBeInTheDocument();
});

/** The chips and the area page swap in one step: the term the box no longer holds is no longer matched. */
test("choosing a matched area puts the chips away at once", async () => {
  const user = userEvent.setup({ delay: null });
  await renderMap();
  await search(user, "f");
  expect(screen.getByText(/^Matches \d+ areas/)).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "flink" }));

  expect(screen.queryByText(/^Matches \d+ areas/)).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "flink" })).toBeInTheDocument();
});

/** Nothing of this area is drawn, so the map only fades: its people have no echo to wait for. */
test("an area with nothing drawn lists who knows it without waiting for an echo", async () => {
  const user = userEvent.setup({ delay: null });
  await renderMap();

  await search(user, "kafka");

  expect(
    await screen.findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
});

/** Stacked, the readings sit away from the box: the answer comes under the drawing, and the readings never move. */
test("lists the answer under the drawing while the readings are stacked", async () => {
  windowOf(412);
  const user = userEvent.setup({ delay: null });
  await renderMap();

  await search(user, "kafka");

  await screen.findByRole("heading", { name: "Who knows kafka" });
  expect(
    within(panel()).queryByRole("heading", { name: "Who knows kafka" }),
  ).not.toBeInTheDocument();
});

test("lists the answer in the readings once they stand beside the drawing", async () => {
  const user = userEvent.setup({ delay: null });
  await renderMap();

  await search(user, "kafka");

  expect(
    await within(panel()).findByRole("heading", { name: "Who knows kafka" }),
  ).toBeInTheDocument();
});

for (const width of [412, 768]) {
  test(`at ${width}px a chosen area and a term naming it list its people once`, async () => {
    windowOf(width);
    const user = userEvent.setup({ delay: null });
    await renderMap();
    await search(user, "f");
    await user.click(screen.getByRole("button", { name: "flink" }));

    await search(user, "flink");

    await screen.findAllByRole("heading", { name: "Who knows flink" });
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(
      screen.getAllByRole("heading", { name: "Who knows flink" }),
    ).toHaveLength(1);
  });
}
