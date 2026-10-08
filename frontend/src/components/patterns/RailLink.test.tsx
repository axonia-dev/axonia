import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  onTestFinished,
  vi,
} from "vitest";

import { RailLink } from "@/components/patterns/RailLink";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";

function Dot() {
  return <svg aria-hidden="true" width="18" height="18" />;
}

function renderLink(children: ReactNode) {
  return render(
    <SidebarProvider>
      <MemoryRouter>
        <div data-testid="rail">{children}</div>
      </MemoryRouter>
    </SidebarProvider>,
  );
}

describe("RailLink", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it("renders an accessible link with a visible label when expanded", () => {
    renderLink(
      <RailLink to="/communities" label="Communities" icon={<Dot />} />,
    );
    const link = screen.getByRole("link", { name: "Communities" });
    expect(link).toHaveAttribute("href", "/communities");
    expect(screen.getByText("Communities")).not.toHaveClass("sr-only");
  });

  it("paints the active link with the primary accent", () => {
    render(
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities"]}>
          <RailLink to="/communities" label="Communities" icon={<Dot />} end />
        </MemoryRouter>
      </SidebarProvider>,
    );
    expect(
      screen.getByRole("link", { name: "Communities" }).className,
    ).toContain("text-primary");
  });

  it("shows no tooltip when expanded, even after focusing the link", async () => {
    const user = userEvent.setup({ delay: null });
    renderLink(
      <RailLink to="/communities" label="Communities" icon={<Dot />} />,
    );
    await user.tab();
    expect(screen.getByRole("link", { name: "Communities" })).toHaveFocus();
    expect(screen.queryByRole("tooltip")).toBeNull();
  });

  it("keeps the accessible name but hides the label visually when collapsed", () => {
    localStorage.setItem("axonia-sidebar-collapsed", "true");
    renderLink(
      <RailLink to="/communities" label="Communities" icon={<Dot />} />,
    );
    expect(
      screen.getByRole("link", { name: "Communities" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Communities")).toHaveClass("sr-only");
    // The hover/focus tooltip is not part of the link's static markup - it only
    // mounts (via a portal) once the link is hovered or focused.
    expect(screen.queryByRole("tooltip")).toBeNull();
  });

  it("reveals the collapsed label as a tooltip on focus, portalled outside the rail", async () => {
    localStorage.setItem("axonia-sidebar-collapsed", "true");
    const user = userEvent.setup({ delay: null });
    renderLink(
      <RailLink to="/communities" label="Communities" icon={<Dot />} />,
    );
    await user.tab();
    const tooltip = await screen.findByRole("tooltip");
    expect(tooltip).toHaveTextContent("Communities");
    // Portalled to document.body: never a descendant of the rail container, so
    // it cannot contribute to the rail's own scrollable overflow.
    expect(
      within(screen.getByTestId("rail")).queryByRole("tooltip"),
    ).toBeNull();
  });

  // Below md the whole rail is hidden, yet a collapsed rail stays collapsed: a tooltip trigger
  // mounted there could never be reached, and react-aria warns about exactly that.
  it("mounts no tooltip trigger at phone width, where the rail is hidden", () => {
    localStorage.setItem("axonia-sidebar-collapsed", "true");
    forceMobileViewport();
    onTestFinished(restoreViewport);
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    onTestFinished(() => warn.mockRestore());

    renderLink(
      <div style={{ display: "none" }}>
        <RailLink to="/communities" label="Communities" icon={<Dot />} />
      </div>,
    );

    expect(warn).not.toHaveBeenCalled();
  });

  it("marks the active link as the current page", () => {
    render(
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities/guild/schedule"]}>
          <RailLink
            to="/communities/guild/schedule"
            label="Schedule"
            icon={<Dot />}
          />
        </MemoryRouter>
      </SidebarProvider>,
    );
    expect(screen.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  // The reported defect: a session is part of the schedule, and a rail that says
  // otherwise is the reason the way back is not obvious.
  it("stays lit on a path the section owns", () => {
    render(
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities/guild/sessions/41"]}>
          <RailLink
            to="/communities/guild/schedule"
            label="Schedule"
            icon={<Dot />}
            owns={["/communities/guild/sessions"]}
          />
        </MemoryRouter>
      </SidebarProvider>,
    );
    const link = screen.getByRole("link", { name: "Schedule" });
    expect(link).toHaveAttribute("aria-current", "page");
    expect(link.className).toContain("text-primary");
  });

  it("is not lit on a path it neither is nor owns", () => {
    render(
      <SidebarProvider>
        <MemoryRouter initialEntries={["/communities/guild/library"]}>
          <RailLink
            to="/communities/guild/schedule"
            label="Schedule"
            icon={<Dot />}
            owns={["/communities/guild/sessions"]}
          />
        </MemoryRouter>
      </SidebarProvider>,
    );
    expect(screen.getByRole("link", { name: "Schedule" })).not.toHaveAttribute(
      "aria-current",
    );
  });
});
