import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, test, vi } from "vitest";

import { ExpertiseMapView } from "@/features/expertise/ui/ExpertiseMapView";

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

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: {
      topics,
      links: [{ a: "debezium", b: "kafka", weight: 1 }],
      namedAreas: [
        { id: 1, name: "Data in Motion", tags: ["kafka", "debezium"] },
        { id: 2, name: "Nothing drawn", tags: ["cobol"] },
      ],
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

test("groups the map by the named areas the map read carries", () => {
  renderMap();

  expect(
    screen.getByText(
      "1 named area. The largest is Data in Motion, with 2 areas. 1 area shares no link with any other.",
    ),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", {
      name: "Data in Motion, named area with 2 areas",
    }),
  ).toBeInTheDocument();
});
