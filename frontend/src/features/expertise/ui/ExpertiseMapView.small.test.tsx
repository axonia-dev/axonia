import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, test, vi } from "vitest";

import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

// Same module mock as ExpertiseMapView.test.tsx; repeat it rather than sharing,
// so this file can be read on its own.
vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: {
      topics: [
        {
          canonicalTag: "kafka",
          practitionerCount: 1,
          signalCount: 2,
          topHolderShare: 1,
          topHolderName: "Dana Krupa",
          presentedCount: 2,
          contributedCount: 0,
          offeredCount: 0,
          lastActivityAt: "2026-08-01T00:00:00Z",
          openToQuestionsCount: 0,
        },
      ],
      links: [],
      namedAreas: [],
    },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/features/expertise/api/use-map-contributions", () => ({
  useMapContributions: () => ({
    data: { people: [], items: [], truncated: false },
  }),
}));

vi.mock("@/features/expertise/ui/TerrainCanvas", () => ({
  TerrainCanvas: () => <div data-testid="terrain-canvas" aria-hidden="true" />,
}));
vi.mock("@/features/expertise/ui/StreamCanvas", () => ({
  StreamCanvas: () => <div data-testid="stream-canvas" aria-hidden="true" />,
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

// jsdom applies no Tailwind and no layout, so nothing here proves what a real
// 375px viewport renders; e2e/expertise-map.spec.ts measures that. These tests
// pin the map-free content path (the panel answers on its own) and the source
// order the phone layout stacks in.

test("the shape statement stands in for the glance", () => {
  renderMap();

  expect(
    screen.getByText(
      "1 area, not yet grouped. 1 area shares no link with any other.",
    ),
  ).toBeInTheDocument();
});

test("the readings come before the map, so a phone opens on an answer", () => {
  renderMap();

  const panel = screen.getByRole("complementary", { name: "Topic readings" });
  const toggle = screen.getByRole("radiogroup", { name: "Picture" });
  expect(
    panel.compareDocumentPosition(toggle) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(toggle.closest(".hidden")).toBeNull();
});
