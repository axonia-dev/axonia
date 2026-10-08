import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import { ExpertisePage } from "@/features/expertise/ui/ExpertisePage";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

// Unlike ExpertisePage.bridge.test.tsx, ExpertiseMapView itself is NOT mocked here: the real
// ReadingPanel selects an area and activates the real ExpertiseMapView.seeWhoKnows, so this test
// is the one that actually exercises the line joining the two views. Only the network-backed
// leaves on either side, and the WebGL drawing, are stood in for.
//
// Two clusters of six, because detectNeighbourhoods draws nothing under MIN_VOCABULARY and nothing
// for a degenerate partition: without a neighbourhood chip there is no area to select at all.
const clusters = [
  ["alpha1", "alpha2", "alpha3", "alpha4", "alpha5", "alpha6"],
  ["beta1", "beta2", "beta3", "beta4", "beta5", "beta6"],
];

function pairs(tags: string[]) {
  return tags.flatMap((a, index) =>
    tags.slice(index + 1).map((b) => ({ a, b, weight: 1 })),
  );
}

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: () => ({
    data: {
      topics: clusters.flat().map((canonicalTag) => ({
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
      links: clusters.flatMap(pairs),
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
vi.mock("@/features/expertise/api/use-expertise-search", () => ({
  useExpertiseSearch: () => ({ data: [], isLoading: false, isError: false }),
}));
vi.mock("@/features/expertise/ui/CommunityKnows", () => ({
  CommunityKnows: () => <div>community knows</div>,
}));
vi.mock("@/features/learning/ui/LearnersBridge", () => ({
  LearnersBridge: () => null,
}));

afterEach(() => localStorage.clear());

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location-search">{location.search}</div>;
}

// A client for the same reason ExpertisePage.bridge.test.tsx holds one: the debounced term mounts
// the real PractitionerList, which queries the standing of everyone it lists.
function renderPage(search: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter
        initialEntries={[`/communities/platform-guild/expertise${search}`]}
      >
        <Routes>
          <Route
            path="/communities/:slug/expertise"
            element={
              <>
                <ExpertisePage />
                <LocationProbe />
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test("selecting an area on the real map view and activating the bridge lands on the search view with the term seeded, every other param kept, and the preference stuck", async () => {
  renderPage("?view=map&foo=bar");

  await user.click(screen.getAllByRole("button", { name: /alongside it/ })[0]!);
  const bridge = screen.getByRole("button", { name: /^See who knows / });
  const tag = bridge.textContent.replace("See who knows ", "");

  await user.click(bridge);

  expect(screen.getByRole("searchbox", { name: /who knows/i })).toHaveValue(
    tag,
  );

  const params = new URLSearchParams(
    screen.getByTestId("location-search").textContent ?? "",
  );
  expect(params.get("view")).toBe("search");
  expect(params.get("term")).toBe(tag);
  expect(params.get("foo")).toBe("bar");
  expect(localStorage.getItem("axonia-expertise-view")).toBe("search");
});
