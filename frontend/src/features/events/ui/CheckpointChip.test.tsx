import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import type { ActionItem } from "@/features/events/domain/action-item";
import { CheckpointChip } from "@/features/events/ui/CheckpointChip";

type Checkpoint = ActionItem["checkpoint"];

function checkpoint(overrides: Partial<Checkpoint> = {}): Checkpoint {
  return {
    sessionId: null,
    scheduledAt: null,
    pinned: false,
    governingTimeZone: null,
    ...overrides,
  };
}

function renderChip(cp: Checkpoint, slug = "backend-guild") {
  render(
    <MemoryRouter>
      <CheckpointChip checkpoint={cp} slug={slug} />
    </MemoryRouter>,
  );
}

describe("CheckpointChip", () => {
  it("states the date alone for an item pinned to one session", () => {
    renderChip(
      checkpoint({
        sessionId: 19,
        scheduledAt: "2026-10-08T09:00:00Z",
        pinned: true,
        governingTimeZone: null,
      }),
    );

    expect(screen.getByText("Thu, Oct 8")).toBeInTheDocument();
  });

  /** An unpinned item is answered at whichever session comes next, so the date is the resolution
   *  of that promise and not a session anyone chose. Dropping "Next session" would turn a
   *  late-binding item into one that claims a pin it does not have. */
  it("keeps saying next session for an unpinned item the server could resolve", () => {
    renderChip(
      checkpoint({
        sessionId: 19,
        scheduledAt: "2026-10-08T09:00:00Z",
        pinned: false,
        governingTimeZone: null,
      }),
    );

    expect(screen.getByText("Next session, Thu, Oct 8")).toBeInTheDocument();
  });

  /* vitest pins TZ=America/Los_Angeles, so an instant at 06:00 UTC on Oct 9 is still Oct 8 for the
   * viewer. A chip that ignored the session's own zone would say Oct 8 and contradict the session
   * card, which reads the same instant on the series' clock. */
  it("reads the date on the session's zone, not the viewer's", () => {
    renderChip(
      checkpoint({
        sessionId: 19,
        scheduledAt: "2026-10-09T06:00:00Z",
        pinned: true,
        governingTimeZone: "Europe/Paris",
      }),
    );

    expect(screen.getByText("Fri, Oct 9")).toBeInTheDocument();
    expect(screen.queryByText("Thu, Oct 8")).not.toBeInTheDocument();
  });

  it("names the promise even when the community has no session to point at", () => {
    renderChip(checkpoint());

    expect(screen.getByText("Next session")).toBeInTheDocument();
  });

  it("states a pinned session with no date at all as next session", () => {
    renderChip(checkpoint({ sessionId: 19, pinned: true }));

    expect(screen.getByText("Next session")).toBeInTheDocument();
  });

  /**
   * The date is read on the viewer's own clock, the fallback arm of the same `governingZone`
   * rule `SessionCard` follows, never hardcoded UTC: the pinned test zone is west of UTC, so a
   * session at 02:00Z meets the evening before and a UTC reading would name the day after the
   * one the session card names.
   */
  it("reads the checkpoint's calendar day on the same clock the session card does", () => {
    renderChip(
      checkpoint({
        sessionId: 19,
        scheduledAt: "2026-10-04T02:00:00Z",
        pinned: true,
        governingTimeZone: null,
      }),
    );

    expect(screen.getByText("Sat, Oct 3")).toBeInTheDocument();
  });

  it("links to the session that answers the item", () => {
    renderChip(
      checkpoint({
        sessionId: 24,
        scheduledAt: "2026-09-24T09:00:00Z",
        pinned: true,
        governingTimeZone: "UTC",
      }),
      "backend-guild",
    );

    expect(screen.getByRole("link")).toHaveAttribute(
      "href",
      "/communities/backend-guild/sessions/24",
    );
  });

  /** A checkpoint with no session resolved names an answer, not an occurrence: there is nothing to
   *  navigate to yet. */
  it("renders no link when no session is resolved", () => {
    renderChip(checkpoint());

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Next session")).toBeInTheDocument();
  });
});
