import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { RunSheet } from "@/features/events/ui/RunSheet";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: ({ section }: { section: { id: number } }) => (
    <div>section {section.id}</div>
  ),
}));

function threeSections() {
  return aScheduledSession({
    scheduledAt: "2026-08-15T18:00:00Z",
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 20,
    sections: [
      aSection({ id: 1, slots: [aSlot({ id: 1, durationMinutes: 10 })] }),
      aSection({ id: 2, slots: [aSlot({ id: 2, durationMinutes: 15 })] }),
      aSection({ id: 3, slots: [aSlot({ id: 3, durationMinutes: 10 })] }),
    ],
  });
}

function renderSheet(session = threeSections()) {
  return render(<RunSheet slug="dojo" session={session} mode="contribute" />);
}

describe("RunSheet", () => {
  it("prints each section's start on the community's own clock", () => {
    renderSheet();
    expect(screen.getByText("20:00")).toBeInTheDocument();
    expect(screen.getByText("20:10")).toBeInTheDocument();
    expect(screen.getByText("20:25")).toBeInTheDocument();
  });

  it("renders one section body per row, in order", () => {
    renderSheet();
    const list = screen.getByRole("list", { name: "Run sheet" });
    expect(
      within(list)
        .getAllByText(/^section /)
        .map((n) => n.textContent),
    ).toEqual(["section 1", "section 2", "section 3"]);
  });

  it("draws one planned-end marker, and says how far past it the session ran", () => {
    renderSheet();
    const marker = screen.getByText(/planned end/);
    expect(marker).toHaveTextContent("planned end · ran 15 min over");
  });

  it("draws no marker at all when no budget was set", () => {
    const session = threeSections();
    renderSheet({ ...session, durationMinutes: null });
    expect(screen.queryByText(/planned end/)).not.toBeInTheDocument();
  });

  it("states a planned end that was met without calling it an overrun", () => {
    const session = threeSections();
    renderSheet({ ...session, durationMinutes: 120 });
    expect(screen.getByText(/planned end/)).toHaveTextContent("planned end");
    expect(screen.queryByText(/over/)).not.toBeInTheDocument();
  });

  it("gives a longer block a taller row, so duration is visible as well as printed", () => {
    renderSheet();
    const rows = screen.getAllByRole("listitem");
    const first = rows[0]?.querySelector<HTMLElement>("[data-part='body']");
    const second = rows[1]?.querySelector<HTMLElement>("[data-part='body']");
    expect(first?.style.minHeight).toBe("44px");
    expect(second?.style.minHeight).toBe("44px");
  });

  it("scales a genuinely long block past the floor", () => {
    const session = aScheduledSession({
      durationMinutes: 60,
      sections: [aSection({ id: 1, slots: [aSlot({ durationMinutes: 40 })] })],
    });
    renderSheet(session);
    const body = screen
      .getAllByRole("listitem")[0]
      ?.querySelector<HTMLElement>("[data-part='body']");
    expect(body?.style.minHeight).toBe("104px");
  });

  it("renders nothing for a session nobody has shaped", () => {
    const { container } = renderSheet(aScheduledSession({ sections: [] }));
    expect(container).toBeEmptyDOMElement();
  });
});
