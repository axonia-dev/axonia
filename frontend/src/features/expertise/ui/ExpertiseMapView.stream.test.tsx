import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test, vi } from "vitest";

import type { MapContributions } from "@/features/expertise/domain/expertise";
import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  contributions = { people: [], items: [], truncated: false };
});

const topics = ["kafka", "debezium", "flink"].map((canonicalTag) => ({
  canonicalTag,
  practitionerCount: 1,
  signalCount: 2,
  topHolderShare: 1,
  topHolderName: "Dana Krupa",
  presentedCount: 2,
  contributedCount: 0,
  offeredCount: 0,
  lastActivityAt: "2026-08-01T00:00:00Z",
  openToQuestionsCount: 0,
}));

let contributions: MapContributions = {
  people: [],
  items: [],
  truncated: false,
};

function withContributions(): MapContributions {
  return {
    people: [{ userId: "u-1", displayName: "Ada Lovelace", avatarUrl: null }],
    items: [
      {
        signalId: 1,
        canonicalTag: "kafka",
        userId: "u-1",
        reach: null,
        evidence: {
          signalType: "PRESENTED",
          sourceType: "SLOT",
          sourceId: 1,
          label: "Scaling Kafka",
          communitySlug: "platform-guild",
          occurredAt: "2026-08-01T00:00:00Z",
          sessionId: null,
          typeId: null,
          typeLabel: null,
          typeIcon: null,
          typeAccentColor: null,
        },
      },
    ],
    truncated: false,
  };
}

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: {
      topics,
      links: [{ a: "debezium", b: "kafka", weight: 1 }],
      namedAreas: [],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/expertise/api/use-map-contributions", () => ({
  useMapContributions: () => ({ data: contributions }),
}));

// three.js never loads under the node suite: what these tests are about is the wiring around the drawing.
vi.mock("@/features/expertise/ui/TerrainCanvas", () => ({
  TerrainCanvas: ({ view }: { view: { zoom: number } }) => (
    <div
      data-testid="terrain-canvas"
      data-zoom={view.zoom}
      aria-hidden="true"
    />
  ),
}));
vi.mock("@/features/expertise/ui/StreamCanvas", () => ({
  StreamCanvas: ({ camera }: { camera: { zoom: number } }) => (
    <div
      data-testid="stream-canvas"
      data-zoom={camera.zoom}
      aria-hidden="true"
    />
  ),
}));

function renderMap() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ExpertiseMapView />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function terrain() {
  return screen.findByTestId("terrain-canvas");
}

/**
 * The stream draws the very same contributions as the terrain and its canvas is aria-hidden too, so without the
 * overlay a mark on that reading could not be clicked, focused or opened by anyone.
 */
test("reaches a mark with a real button on the stream, not only on the terrain", async () => {
  contributions = withContributions();
  renderMap();
  await terrain();

  await user.click(screen.getByRole("radio", { name: "Over time" }));
  await screen.findByTestId("stream-canvas");

  expect(
    screen.getByRole("button", { name: /Scaling Kafka/ }),
  ).toBeInTheDocument();
});

test("opens the one strip from the stream, the same as from the terrain", async () => {
  contributions = withContributions();
  renderMap();
  await terrain();
  await user.click(screen.getByRole("radio", { name: "Over time" }));
  await screen.findByTestId("stream-canvas");

  await user.click(screen.getByRole("button", { name: /Scaling Kafka/ }));

  expect(
    within(screen.getByTestId("contribution-strip")).getByText("kafka"),
  ).toBeInTheDocument();
});

/** The stream was once the one reading with no zoom and no drag at all: a decade of contributions as a smear. */
test("hands the stream a camera that zooms too", async () => {
  renderMap();
  await terrain();
  await user.click(screen.getByRole("radio", { name: "Over time" }));
  const stream = await screen.findByTestId("stream-canvas");

  await user.click(screen.getByRole("button", { name: "Zoom in" }));

  expect(Number(stream.dataset["zoom"])).toBeGreaterThan(1);
});

/** Each reading is aimed and framed on its own terms, so what the reader turned the globe to says nothing about
 *  where the history should start. */
test("sends the camera home when the reading changes", async () => {
  renderMap();
  await terrain();
  await user.click(screen.getByRole("button", { name: "Zoom in" }));

  await user.click(screen.getByRole("radio", { name: "Over time" }));

  expect(
    Number((await screen.findByTestId("stream-canvas")).dataset["zoom"]),
  ).toBe(1);
});

test("clears the selection when the stream's own drawing is clicked rather than a mark", async () => {
  contributions = withContributions();
  renderMap();
  await terrain();
  await user.click(screen.getByRole("radio", { name: "Over time" }));
  await user.click(screen.getByRole("button", { name: /Scaling Kafka/ }));

  await user.click(screen.getByTestId("stream-canvas"));

  expect(screen.queryByTestId("contribution-strip")).not.toBeInTheDocument();
});
