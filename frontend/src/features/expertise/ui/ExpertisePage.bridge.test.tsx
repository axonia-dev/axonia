import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import { ExpertisePage } from "@/features/expertise/ui/ExpertisePage";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

// The map view is exercised on its own in ExpertiseMapView.test.tsx; here only the flip
// through it matters, so it stays a stand-in that never fetches.
vi.mock("@/features/expertise/ui/ExpertiseMapView", () => ({
  ExpertiseMapView: () => <div>map view</div>,
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

// The search view debounces its term by 300ms, and the term is what mounts the real
// PractitionerList, which reads the practitioners' rings over the network. A run slow enough for
// that timer to fire mid-test - CI is - therefore renders a query where the app always has a
// client, so the harness has to hold one too or the whole tree tears down.
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
            element={<ExpertisePage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test("the search term survives a flip to the map and back", async () => {
  renderPage("?term=kafka");

  expect(screen.getByRole("searchbox", { name: /who knows/i })).toHaveValue(
    "kafka",
  );

  await user.click(screen.getByRole("radio", { name: "Map" }));

  expect(screen.getByText("map view")).toBeInTheDocument();

  await user.click(screen.getByRole("radio", { name: "Search" }));

  expect(screen.getByRole("searchbox", { name: /who knows/i })).toHaveValue(
    "kafka",
  );
});
