import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SessionRail } from "@/features/events/ui/SessionRail";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

function bigSession() {
  return aScheduledSession({
    durationMinutes: 90,
    sections: [
      aSection({
        id: 1,
        sectionType: "Ice Breaker",
        slots: [aSlot({ id: 1, durationMinutes: 10 })],
      }),
      aSection({
        id: 2,
        sectionType: "Lightning Talk",
        slots: [aSlot({ id: 2, durationMinutes: 15 })],
      }),
      aSection({
        id: 3,
        sectionType: "Deep Dive",
        slots: [aSlot({ id: 3, durationMinutes: 30 })],
      }),
      aSection({
        id: 4,
        sectionType: "Q&A",
        slots: [aSlot({ id: 4, durationMinutes: 10 })],
      }),
    ],
  });
}

describe("SessionRail", () => {
  it("names every section with the minutes it runs", () => {
    render(
      <SessionRail session={bigSession()} phase="upcoming" activeId={null} />,
    );
    const nav = screen.getByRole("navigation", { name: "This session" });
    expect(
      within(nav).getByRole("link", { name: /Ice Breaker/ }),
    ).toHaveTextContent("10");
    expect(
      within(nav).getByRole("link", { name: /Deep Dive/ }),
    ).toHaveTextContent("30");
  });

  it("anchors each row at the section it names", () => {
    render(
      <SessionRail session={bigSession()} phase="upcoming" activeId={null} />,
    );
    expect(screen.getByRole("link", { name: /Deep Dive/ })).toHaveAttribute(
      "href",
      "#run-sheet-section-3",
    );
  });

  it("marks the row the reader is inside, and only that one", () => {
    render(
      <SessionRail
        session={bigSession()}
        phase="upcoming"
        activeId="run-sheet-section-2"
      />,
    );
    const current = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("aria-current") === "location");
    expect(current).toHaveLength(1);
    expect(current[0]).toHaveTextContent("Lightning Talk");
  });

  it("states the session total and how far it ran over", () => {
    render(<SessionRail session={bigSession()} phase="held" activeId={null} />);
    expect(screen.getByText(/65 min/)).toBeInTheDocument();
  });

  it("offers the outcome anchors only once the session is held", () => {
    const { rerender } = render(
      <SessionRail session={bigSession()} phase="upcoming" activeId={null} />,
    );
    expect(
      screen.queryByRole("link", { name: /Action points/ }),
    ).not.toBeInTheDocument();
    rerender(
      <SessionRail session={bigSession()} phase="held" activeId={null} />,
    );
    expect(
      screen.getByRole("link", { name: /Action points/ }),
    ).toBeInTheDocument();
  });
});
