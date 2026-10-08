import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SessionOutcomes } from "@/features/events/ui/SessionOutcomes";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => <p>action points</p>,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => <p>resources</p>,
}));

const held = aScheduledSession({
  status: "HELD",
  scheduledAt: "2026-08-15T18:00:00Z",
  sections: [aSection({ slots: [aSlot()] })],
});

describe("SessionOutcomes", () => {
  it("leads a held session with its action points, then its resources", () => {
    render(<SessionOutcomes slug="dojo" session={held} canManage />);
    const points = screen.getByText("action points");
    const resources = screen.getByText("resources");
    expect(points.compareDocumentPosition(resources)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("says nothing on a session that has not happened yet", () => {
    const { container } = render(
      <SessionOutcomes
        slug="dojo"
        session={aScheduledSession({ scheduledAt: "2099-01-01T10:00:00Z" })}
        canManage
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing while a past session is still waiting on an answer", () => {
    const { container } = render(
      <SessionOutcomes
        slug="dojo"
        session={aScheduledSession({ scheduledAt: "2020-01-01T10:00:00Z" })}
        canManage
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing for a cancelled session", () => {
    const { container } = render(
      <SessionOutcomes
        slug="dojo"
        session={{ ...held, status: "CANCELLED" }}
        canManage
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("quotes the note ahead of the action points", () => {
    render(
      <SessionOutcomes
        slug="dojo"
        session={{ ...held, notes: "The p95 numbers landed." }}
        canManage
      />,
    );
    const note = screen.getByText("The p95 numbers landed.");
    expect(
      note.compareDocumentPosition(screen.getByText("action points")),
    ).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
  });

  /** An absent note is absent: a row spent on it is a gap above the action points. */
  it("spends no row on a note the session never got", () => {
    const { container } = render(
      <SessionOutcomes
        slug="dojo"
        session={{ ...held, notes: null }}
        canManage
      />,
    );
    expect(container.firstElementChild?.childElementCount).toBe(2);
  });
});
