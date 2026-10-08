import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { expect, test, vi } from "vitest";

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
      zoom: number;
      spin?: { yaw: number; pitch: number };
      pan?: { x: number; y: number };
    };
  }) => (
    <div
      data-testid="terrain-canvas"
      data-yaw={view.spin?.yaw}
      data-pitch={view.spin?.pitch}
      data-pan-x={view.pan?.x}
      data-zoom={view.zoom}
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

/** Resolves once the lazily loaded canvas is up, since every press lands on the box it sits in. */
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

async function canvas() {
  return screen.findByTestId("terrain-canvas");
}

function drawing(): HTMLElement {
  return screen.getByTestId("terrain-canvas").parentElement!;
}

function press(pointerId: number, clientX: number, clientY = 300) {
  fireEvent.pointerDown(drawing(), { pointerId, button: 0, clientX, clientY });
}

function move(pointerId: number, clientX: number, clientY = 300) {
  fireEvent.pointerMove(drawing(), { pointerId, clientX, clientY });
}

function lift(pointerId: number) {
  fireEvent.pointerUp(drawing(), { pointerId });
}

/** The globe's own turn per pixel, restated so the test says what a ten pixel drag should do. */
const DEGREES_PER_PX = 0.28;

const yawOf = (element: HTMLElement) => Number(element.dataset["yaw"]);
const zoomOf = (element: HTMLElement) => Number(element.dataset["zoom"]);

test("two fingers spread apart zoom the picture in", async () => {
  await renderMap();

  press(1, 300);
  press(2, 500);
  move(2, 700);
  lift(2);
  lift(1);

  expect(zoomOf(await canvas())).toBeGreaterThan(1);
});

test("two fingers drawn together zoom it out", async () => {
  await renderMap();

  press(1, 300);
  press(2, 500);
  move(2, 400);

  expect(zoomOf(await canvas())).toBeLessThan(1);
});

/** Measured between two different fingers, each move threw the picture the width of the gap between them. */
test("a second finger does not throw the picture", async () => {
  await renderMap();

  press(1, 300);
  press(2, 500);
  move(2, 500);
  move(1, 300);

  expect(yawOf(await canvas())).toBe(restingSpin("globe").yaw);
});

test("the finger left after a pinch carries on from where it is", async () => {
  await renderMap();

  press(1, 300);
  press(2, 500);
  move(1, 320);
  lift(2);
  move(1, 330);

  const zoom = zoomOf(await canvas());
  expect(yawOf(await canvas())).toBeCloseTo(-(10 * DEGREES_PER_PX) / zoom);
});

/**
 * A finger on a picture with nowhere to go is a finger on the page. The globe always has a far side to turn to,
 * and the flat reading has somewhere to go once it is zoomed in; at rest it has none, and the page scrolls.
 */
test("the flat reading at rest lets a vertical swipe scroll the page", async () => {
  const user = userEvent.setup({ delay: null });
  await renderMap();
  expect(drawing()).toHaveClass("touch-none");

  await user.click(screen.getByRole("radio", { name: "Flat" }));
  expect(drawing()).toHaveClass("touch-pan-y");

  await user.click(screen.getByRole("button", { name: "Zoom in" }));
  expect(drawing()).toHaveClass("touch-none");
});
