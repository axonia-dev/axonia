import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

import { ExpertisePage } from "@/features/expertise/ui/ExpertisePage";

vi.mock("@/features/expertise/ui/ExpertiseSearchView", () => ({
  ExpertiseSearchView: () => <div>search view</div>,
}));
vi.mock("@/features/expertise/ui/ExpertiseMapView", () => ({
  ExpertiseMapView: () => <div>map view</div>,
}));

import { storeViewMode } from "@/features/expertise/ui/expertise-preferences";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

afterEach(() => localStorage.clear());

function renderPage(search = "") {
  return render(
    <MemoryRouter
      initialEntries={[`/communities/platform-guild/expertise${search}`]}
    >
      <Routes>
        <Route
          path="/communities/:slug/expertise"
          element={<ExpertisePage />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

test("the search view is what a member lands on", () => {
  renderPage();

  expect(screen.getByText("search view")).toBeInTheDocument();
  expect(screen.queryByText("map view")).not.toBeInTheDocument();
  expect(localStorage.getItem("axonia-expertise-view")).toBeNull();
});

test("choosing Map swaps the whole view", async () => {
  renderPage();

  await user.click(screen.getByRole("radio", { name: "Map" }));

  expect(screen.getByText("map view")).toBeInTheDocument();
  expect(screen.queryByText("search view")).not.toBeInTheDocument();
  expect(localStorage.getItem("axonia-expertise-view")).toBe("map");
});

test("the url wins over a stored preference", () => {
  storeViewMode("map");

  renderPage("?view=search");

  expect(screen.getByText("search view")).toBeInTheDocument();
});

test("a stored preference wins when the url says nothing", () => {
  storeViewMode("map");

  renderPage();

  expect(screen.getByText("map view")).toBeInTheDocument();
});

/**
 * The shell gives this route the wide content width, because at the reading width the map's own column came out
 * under 400px on every screen size. The search view is a list and still reads at that width, and the page is what
 * decides between the two: the URL alone cannot, since a stored preference answers when it says nothing.
 */
test("the map takes the room the route was widened for, and the search list does not", async () => {
  renderPage();
  const section = screen.getByText("search view").closest("section");
  expect(section).toHaveClass("max-w-3xl");

  await user.click(screen.getByRole("radio", { name: "Map" }));

  const widened = screen.getByText("map view").closest("section");
  expect(widened).not.toHaveClass("max-w-3xl");
  expect(widened).toHaveClass("max-w-[1600px]");
});
