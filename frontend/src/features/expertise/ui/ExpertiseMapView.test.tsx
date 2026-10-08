import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
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

test("draws the terrain first", async () => {
  renderMap();

  expect(await terrain()).toBeInTheDocument();
  expect(screen.queryByTestId("stream-canvas")).not.toBeInTheDocument();
});

test("switches to the stream", async () => {
  renderMap();
  await terrain();

  await user.click(screen.getByRole("radio", { name: "Over time" }));

  expect(await screen.findByTestId("stream-canvas")).toBeInTheDocument();
  expect(screen.queryByTestId("terrain-canvas")).not.toBeInTheDocument();
});

test("offers globe and flat only while the terrain is showing", async () => {
  renderMap();
  await terrain();

  expect(screen.getByRole("radio", { name: "Globe" })).toBeInTheDocument();

  await user.click(screen.getByRole("radio", { name: "Over time" }));

  expect(
    screen.queryByRole("radio", { name: "Globe" }),
  ).not.toBeInTheDocument();
});

test("says so when the picture was capped, and says where the cap falls", async () => {
  contributions = { people: [], items: [], truncated: true };
  renderMap();
  await terrain();

  expect(
    screen.getByText(/showing the most recent 5,000 contributions/i),
  ).toBeInTheDocument();
  expect(screen.getByText(/older activity is not drawn/i)).toBeInTheDocument();
});

test("says nothing about capping when it was not capped", async () => {
  renderMap();
  await terrain();

  expect(screen.queryByText(/most recent 5,000/i)).not.toBeInTheDocument();
});

test("leaves the readings panel exactly where it was", () => {
  renderMap();

  expect(
    screen.getByRole("complementary", { name: "Topic readings" }),
  ).toBeInTheDocument();
});

test("every topic the map draws is also named in the panel", async () => {
  renderMap();

  await user.click(screen.getByRole("button", { name: /reading/i }));
  await user.click(screen.getByRole("option", { name: "Recently active" }));

  const panel = within(
    screen.getByRole("complementary", { name: "Topic readings" }),
  );
  for (const t of topics) {
    expect(panel.getByText(t.canonicalTag)).toBeInTheDocument();
  }
});

test("the drawing stays decorative, because the panel beside it is complete", async () => {
  renderMap();

  expect(await terrain()).toHaveAttribute("aria-hidden", "true");
});

test("reaches the zoom without a mouse, and stops at each end of the range", async () => {
  renderMap();
  await terrain();

  const zoomIn = screen.getByRole("button", { name: "Zoom in" });
  const zoomOut = screen.getByRole("button", { name: "Zoom out" });
  expect(zoomIn).toBeEnabled();
  expect(zoomOut).toBeEnabled();

  for (let click = 0; click < 8; click++) await user.click(zoomOut);

  expect(zoomOut).toBeDisabled();
  expect(zoomIn).toBeEnabled();

  for (let click = 0; click < 24; click++) await user.click(zoomIn);

  expect(zoomIn).toBeDisabled();
});

test("zooming in hands the canvas a closer camera", async () => {
  renderMap();
  const before = (await terrain()).dataset["zoom"];

  await user.click(screen.getByRole("button", { name: "Zoom in" }));

  expect(Number((await terrain()).dataset["zoom"])).toBeGreaterThan(
    Number(before),
  );
});

function wheelOver(canvas: HTMLElement, event: Partial<WheelEventInit>) {
  const wheel = new WheelEvent("wheel", {
    bubbles: true,
    cancelable: true,
    ...event,
  });
  fireEvent(canvas, wheel);
  return wheel;
}

/**
 * The drawing takes a share of the viewport rather than sitting in a card the page scrolls past, so a wheel over it
 * is a wheel over the map and there is no modifier to discover. A modifier held over it is still a zoom rather than
 * the browser's own page zoom landing on the drawing. React's own onWheel is passive, so preventDefault there warns
 * and the page scrolls under the reader instead: the listener is native and non-passive.
 */
test.each([{}, { ctrlKey: true }, { metaKey: true }])(
  "zooms in on a wheel (%o), and keeps the page still while it does",
  async (modifier) => {
    renderMap();

    const wheel = wheelOver(await terrain(), { deltaY: -120, ...modifier });

    expect(wheel.defaultPrevented).toBe(true);
    expect(Number((await terrain()).dataset["zoom"])).toBeGreaterThan(1);
  },
);

test("zooms back out on a wheel the other way", async () => {
  renderMap();

  wheelOver(await terrain(), { deltaY: 120 });

  expect(Number((await terrain()).dataset["zoom"])).toBeLessThan(1);
});
