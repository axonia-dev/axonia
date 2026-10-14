import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import type {
  MapContributions,
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { DrawnHighlight } from "@/features/expertise/ui/echo-transition";
import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  seen.length = 0;
});

afterEach(() => {
  vi.restoreAllMocks();
});

function lastTransition() {
  return seen.at(-1)!.transition;
}

async function settle() {
  await act(() => new Promise((resolve) => setTimeout(resolve, 260)));
}

function box() {
  return screen.getByRole("searchbox", { name: /find on the map/i });
}

function preferReducedMotion() {
  const real = window.matchMedia;
  vi.spyOn(window, "matchMedia").mockImplementation((query) => ({
    ...real(query),
    matches: query.includes("prefers-reduced-motion"),
  }));
}

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

const seen: DrawnHighlight[] = [];

vi.mock("@/features/expertise/ui/MapDrawing", () => ({
  MapDrawing: ({ drawn }: { drawn: DrawnHighlight }) => {
    seen.push(drawn);
    return <div data-testid="drawing" />;
  },
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
  await screen.findByTestId("drawing");
}

test("a settled term echoes", async () => {
  await renderMap();

  await user.type(box(), "kafka");
  await settle();

  expect(lastTransition()).toBe("echo");
});

test("clearing the box fades", async () => {
  await renderMap();
  await user.type(box(), "kafka");
  await settle();

  await user.click(screen.getByRole("button", { name: /clear search/i }));
  await settle();

  expect(lastTransition()).toBe("fade");
});

test("a reading chosen in the panel snaps", async () => {
  await renderMap();

  await user.click(screen.getByRole("button", { name: /reading/i }));
  await user.click(screen.getByRole("option", { name: "Not yet presented" }));

  expect(lastTransition()).toBe("snap");
});

test("reduced motion fades a settled term instead of echoing it", async () => {
  preferReducedMotion();
  await renderMap();

  await user.type(box(), "kafka");
  await settle();

  expect(lastTransition()).toBe("fade");
});
