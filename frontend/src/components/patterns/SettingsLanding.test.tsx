import { render, screen } from "@testing-library/react";
import { Building2 } from "lucide-react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { SettingsGroup } from "@/components/patterns/settings-nav";
import { SettingsLanding } from "@/components/patterns/SettingsLanding";

const originalMatchMedia = window.matchMedia;

function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches,
    media: "",
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  });
}

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

const groups: ReadonlyArray<SettingsGroup> = [
  {
    label: "Organization",
    items: [
      {
        path: "general",
        label: "Organization",
        icon: Building2,
      },
      {
        path: "users",
        label: "Users",
        icon: Building2,
      },
    ],
  },
];

function renderLanding(desktop: boolean) {
  stubMatchMedia(desktop);
  return render(
    <MemoryRouter initialEntries={["/settings/organization"]}>
      <Routes>
        <Route
          path="/settings/organization"
          element={
            <SettingsLanding
              title="Your organization"
              base="/settings/organization"
              groups={groups}
            />
          }
        />
        <Route
          path="/settings/organization/general"
          element={<p>General page</p>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("SettingsLanding", () => {
  it("redirects to the first real section on desktop", () => {
    renderLanding(true);
    expect(screen.getByText("General page")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Your organization" }),
    ).not.toBeInTheDocument();
  });

  it("shows the overview list below md, reading the same groups", () => {
    renderLanding(false);
    expect(
      screen.getByRole("heading", { name: "Your organization" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Users")).toBeInTheDocument();
    expect(screen.queryByText("General page")).not.toBeInTheDocument();
  });

  it("falls back to the overview on desktop when there is no first section", () => {
    stubMatchMedia(true);
    render(
      <MemoryRouter initialEntries={["/"]}>
        <SettingsLanding
          title="Your organization"
          base="/settings/organization"
          groups={[]}
        />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("heading", { name: "Your organization" }),
    ).toBeInTheDocument();
  });
});
