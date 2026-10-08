import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test, vi } from "vitest";

import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";
import { restingSpin } from "@/features/expertise/ui/terrain-aim";

// The same module mocks the sibling files use, repeated rather than shared so this file can be read on its own.
vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: {
      topics: ["kafka", "flink"].map((canonicalTag) => ({
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
      })),
      links: [],
      namedAreas: [],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/expertise/api/use-map-contributions", () => ({
  useMapContributions: () => ({
    data: {
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
    },
  }),
}));

vi.mock("@/features/expertise/ui/TerrainCanvas", () => ({
  TerrainCanvas: ({
    view,
  }: {
    view: {
      spin?: { yaw: number; pitch: number };
      pan?: { x: number; y: number };
    };
  }) => (
    <div
      data-testid="terrain-canvas"
      data-yaw={view.spin?.yaw}
      data-pitch={view.spin?.pitch}
      data-pan-x={view.pan?.x}
      aria-hidden="true"
    />
  ),
}));
vi.mock("@/features/expertise/ui/StreamCanvas", () => ({
  StreamCanvas: ({ camera }: { camera: { pan: { x: number } } }) => (
    <div
      data-testid="stream-canvas"
      data-pan-x={camera.pan.x}
      aria-hidden="true"
    />
  ),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

// The canvases are lazy and a module resolves once per file, so only the first test to render would
// otherwise find the drawing still suspended.
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
  await canvas();
}

async function canvas() {
  return screen.findByTestId("terrain-canvas");
}

/** The element the pointer handlers sit on: whichever canvas is showing, and every overlay button, sits inside it. */
function drawing(): HTMLElement {
  const showing =
    screen.queryByTestId("terrain-canvas") ??
    screen.getByTestId("stream-canvas");
  return showing.parentElement!;
}

function drag(from: HTMLElement, dx: number, dy: number) {
  fireEvent.pointerDown(from, {
    pointerId: 1,
    button: 0,
    clientX: 400,
    clientY: 300,
  });
  fireEvent.pointerMove(drawing(), {
    pointerId: 1,
    clientX: 400 + dx,
    clientY: 300 + dy,
  });
  fireEvent.pointerUp(drawing(), { pointerId: 1 });
}

const yawOf = (element: HTMLElement) => Number(element.dataset["yaw"]);
const pitchOf = (element: HTMLElement) => Number(element.dataset["pitch"]);
const panXOf = (element: HTMLElement) => Number(element.dataset["panX"]);

async function zoomIn(times: number) {
  const button = screen.getByRole("button", { name: "Zoom in" });
  for (let click = 0; click < times; click++) await user.click(button);
}

test("starts the globe where the camera rests", async () => {
  await renderMap();

  expect(yawOf(await canvas())).toBe(restingSpin("globe").yaw);
  expect(pitchOf(await canvas())).toBe(restingSpin("globe").pitch);
});

/** The globe hides a hemisphere by construction. Dragging is how a reader goes and looks at it. */
test("turns the globe when it is dragged", async () => {
  await renderMap();

  drag(drawing(), 200, 60);

  expect(yawOf(await canvas())).toBeLessThan(0);
  expect(pitchOf(await canvas())).toBeGreaterThan(restingSpin("globe").pitch);
});

test("turns the other way when dragged the other way", async () => {
  await renderMap();

  drag(drawing(), -200, -60);

  expect(yawOf(await canvas())).toBeGreaterThan(0);
  expect(pitchOf(await canvas())).toBeLessThan(restingSpin("globe").pitch);
});

// The camera's basis is built against world up, where "right" stops being defined at the pole itself.
test("will not drag the eye past the pole", async () => {
  await renderMap();

  drag(drawing(), 0, 4000);

  expect(pitchOf(await canvas())).toBeLessThanOrEqual(82);
});

/** Flat and globe are one layout under two cameras, and a reader coming back should find the picture at rest. */
test("puts the globe back at rest after a trip through the flat reading", async () => {
  await renderMap();
  drag(drawing(), 200, 0);
  expect(yawOf(await canvas())).not.toBe(0);

  await user.click(screen.getByRole("radio", { name: "Flat" }));
  await user.click(screen.getByRole("radio", { name: "Globe" }));

  expect(yawOf(await canvas())).toBe(restingSpin("globe").yaw);
});

test("leaves the flat reading alone, since turning a map only rotates its north", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));

  drag(drawing(), 200, 60);

  expect(yawOf(await canvas())).toBe(restingSpin("flat").yaw);
});

/**
 * The flat reading and the stream have no far side to go and look at. What they have instead is more of themselves
 * than the frame holds once a reader has zoomed in, so a drag carries the frame across them. Both were once
 * unreachable past the edges of the box.
 */
test("carries the frame across the flat reading when it is dragged", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));
  await zoomIn(6);

  drag(drawing(), -200, 0);

  expect(panXOf(await canvas())).toBeGreaterThan(0);
});

test("carries the frame across the stream the same way", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Over time" }));
  await zoomIn(6);
  await screen.findByTestId("stream-canvas");

  drag(drawing(), -200, 0);

  expect(panXOf(screen.getByTestId("stream-canvas"))).toBeGreaterThan(0);
});

/** Panned to the very edge, the frame would otherwise slide off the drawing and leave the reader on blank ground. */
test("stops the frame at the edge of the drawing", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));
  await zoomIn(6);
  drag(drawing(), -5000, 0);
  const far = panXOf(await canvas());

  drag(drawing(), -5000, 0);

  expect(panXOf(await canvas())).toBe(far);
});

/** At rest the whole drawing is in frame, so there is nowhere for a drag to carry it. */
test("has nowhere to carry the frame before the reader has zoomed in", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));

  drag(drawing(), -200, 0);

  expect(panXOf(await canvas())).toBe(0);
});

/** A right press belongs to the browser: a context menu left open over a picture that had shifted under it. */
test("ignores a press that is not the primary button", async () => {
  await renderMap();
  await user.click(screen.getByRole("radio", { name: "Flat" }));
  await zoomIn(6);

  fireEvent.pointerDown(drawing(), {
    pointerId: 1,
    button: 2,
    clientX: 400,
    clientY: 300,
  });
  fireEvent.pointerMove(drawing(), {
    pointerId: 1,
    clientX: 200,
    clientY: 300,
  });
  fireEvent.pointerUp(drawing(), { pointerId: 1 });

  expect(panXOf(await canvas())).toBe(0);
});

/**
 * A press that turned the globe is not a click on what happens to be under the hand when it stops: ending over the
 * ground must not put the strip away, and ending over a mark must not open a different one.
 */
test("a drag that ends on the drawing does not put the open strip away", async () => {
  await renderMap();
  await user.click(screen.getByRole("button", { name: /Scaling Kafka/ }));
  expect(screen.getByTestId("contribution-strip")).toBeInTheDocument();

  drag(drawing(), 150, 0);
  fireEvent.click(await canvas());

  expect(screen.getByTestId("contribution-strip")).toBeInTheDocument();
});

test("a press whose hand only wandered still opens the mark under it", async () => {
  await renderMap();
  const mark = screen.getByRole("button", { name: /Scaling Kafka/ });

  drag(mark, 2, 1);
  fireEvent.click(mark);

  expect(screen.getByTestId("contribution-strip")).toBeInTheDocument();
});
