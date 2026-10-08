import { render, screen } from "@testing-library/react";
import type { ComponentProps } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { aScheduledSession, aSection } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: ({ section }: { section: { id: number } }) => (
    <div>section {section.id}</div>
  ),
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => null,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => <button type="button">+ Action point</button>,
}));
vi.mock("@/features/events/ui/ReleasePreviewDialog", () => ({
  ReleasePreviewDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div role="dialog">Wrap-up preview</div> : null,
}));

const ROOM = "https://meet.example/backend-guild";

const session: Session = aScheduledSession({
  id: 9,
  seriesId: 1,
  occurrenceDate: null,
  scheduledAt: "2099-01-01T18:00:00Z",
  facilitatorUserId: null,
  attendeeCount: 4,
  sections: [aSection({ sectionType: "Talk", title: "Main talk" })],
});

type CardProps = ComponentProps<typeof SessionCard>;

/** The phase and status chip a real page would derive for this session, so a test can override
 *  only the one fact it is actually about. */
function renderCard(
  session: Session,
  overrides: Partial<Omit<CardProps, "session">> = {},
) {
  return render(
    <SessionCard
      slug="backend-guild"
      session={session}
      phase={sessionPhase(session)}
      statusChip={sessionStatusChip(session)}
      {...overrides}
    />,
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe("SessionCard", () => {
  it("renders the derived title and sections", () => {
    renderCard(session);
    expect(screen.getByText("Pairing on hexagons")).toBeInTheDocument();
    expect(screen.getByText("section 3")).toBeInTheDocument();
  });

  it("does not render an attendance toggle", () => {
    renderCard(session);
    expect(
      screen.queryByRole("button", { name: /i'm going/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/going/i)).not.toBeInTheDocument();
  });

  /**
   * Stated once, by the facts strip alone: the header names only the date now (R24), so an
   * occurrence nobody set an hour for cannot say so twice from two different places any more.
   */
  it("states that an occurrence nobody set an hour for is still waiting on one", () => {
    const untimed = { ...session, hasTime: false };
    renderCard(untimed);
    expect(screen.getAllByText(/Time to be confirmed/)).toHaveLength(1);
    expect(screen.queryByText(/10:00/)).not.toBeInTheDocument();
  });

  /**
   * The session's own clock, not the reader's: `scheduledAt` is 18:00Z, which the suite's pinned
   * `America/Los_Angeles` would read as 10:00 - the bug R24 fixes - but the series governs this
   * session from `Europe/Paris`, where the same instant is 19:00. Only the facts strip states an
   * hour at all now; the header states the date alone.
   */
  it("shows the hour of a session somebody did set one for, on the session's own clock", () => {
    renderCard(session);
    expect(screen.getByText("19:00 to 20:00")).toBeInTheDocument();
    expect(screen.queryByText(/10:00/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Time to be confirmed/)).not.toBeInTheDocument();
  });

  /**
   * The same agenda and the same booking must still end at different hours depending on the
   * phase. Upcoming is a promise about the future - the session is booked 19:00 to 20:00 - and
   * has not yet had the chance to run short, even though its one talk only fills 20 of those
   * minutes. Held is a record of the past: it ran exactly as long as its one talk did.
   */
  it("ends an upcoming session at the hour it was booked for, and a held one at the hour its agenda actually ran, given the identical agenda and booking", () => {
    const upcoming = renderCard(session);
    expect(upcoming.getByText("19:00 to 20:00")).toBeInTheDocument();

    const held = renderCard({ ...session, status: "HELD" });
    expect(held.getByText("19:00 to 19:20")).toBeInTheDocument();
  });

  /**
   * R24: the header's subline is a date, read in the zone that governs the session rather than in
   * whichever zone the reader's own browser happens to be set to. `01:00Z` is chosen because it
   * falls on two different calendar days depending which zone reads it: 03:00 the same day in
   * `Europe/Paris`, but still the evening before in the suite's pinned `America/Los_Angeles` - a
   * case only the session's own zone can pass.
   */
  it("reads the header's date in the session's own zone, not the viewer's", () => {
    renderCard({ ...session, scheduledAt: "2026-08-15T01:00:00Z" });
    expect(screen.getAllByText("Sat, Aug 15")).toHaveLength(2);
    expect(screen.queryByText(/Aug 14/)).not.toBeInTheDocument();
  });

  it("renders supplied actions for the session", () => {
    renderCard(session, {
      actions: <button type="button">Save to library</button>,
    });
    expect(
      screen.getByRole("button", { name: /save to library/i }),
    ).toBeInTheDocument();
  });

  it("renders supplied headerActions in the card header", () => {
    renderCard(session, {
      headerActions: <button type="button">Session menu</button>,
    });
    expect(
      screen.getByRole("button", { name: /session menu/i }),
    ).toBeInTheDocument();
  });

  /**
   * The location is told twice while the session is still upcoming: once as a quick fact
   * alongside the run count, once as a full block with the joining details next to it. The
   * dial-in block itself - the part nobody reads twice - is told once.
   */
  it("names where the session meets and what it takes to get in", () => {
    renderCard({
      ...session,
      place: {
        location: "Room 4B, Gasperich",
        joinUrl: null,
        joiningDetails: "Dial +352 27 00 00\nID 123 456 789",
      },
    });

    expect(screen.getAllByText("Room 4B, Gasperich")).toHaveLength(2);
    expect(screen.getByText(/Dial \+352 27 00 00/).textContent).toBe(
      "Dial +352 27 00 00\nID 123 456 789",
    );
  });

  /**
   * The card the detail page renders for EVERY status, so an ungated passcode would sit on a
   * cancelled session's page for good - on the same card whose way in deliberately vanished. Once
   * closed, the place block itself is withheld, so the location is named exactly once, by the
   * facts row rather than by the block that used to carry it.
   */
  it("stops showing a cancelled session's passcode while still naming where it would have met", () => {
    renderCard({
      ...session,
      status: "CANCELLED",
      place: {
        location: "Room 4B, Gasperich",
        joinUrl: null,
        joiningDetails: "Passcode 4242",
      },
    });

    expect(screen.queryByText("Passcode 4242")).not.toBeInTheDocument();
    expect(screen.getByText("Room 4B, Gasperich")).toBeInTheDocument();
  });

  it("offers no way in when the session records no room", () => {
    renderCard(session);
    expect(
      screen.queryByRole("button", { name: "Join" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Join now" }),
    ).not.toBeInTheDocument();
  });

  it("shows the room shut, once, while the session is still hours out", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2099-01-01T12:00:00Z"));
    renderCard({ ...session, place: { ...EMPTY_PLACE, joinUrl: ROOM } });

    expect(screen.getAllByRole("button", { name: "Join" })).toHaveLength(1);
    expect(screen.queryAllByRole("status")).toHaveLength(0);
  });

  /**
   * R25: the header's own `StatusChip` is now the one place the page states this - both
   * `JoinControl` instances pass `phaseStatedElsewhere`, so neither repeats it as its own chip.
   */
  it("tells the page a session is starting soon once, from the header, before any way in exists", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2099-01-01T17:30:00Z"));
    renderCard({ ...session, place: { ...EMPTY_PLACE, joinUrl: ROOM } });

    expect(screen.getAllByText("Starting soon")).toHaveLength(1);
    expect(
      screen.queryByRole("link", { name: "Join now" }),
    ).not.toBeInTheDocument();
  });

  /**
   * R26: the header's own `StatusChip` distinguishes a session still inside its scheduled window
   * from one that has finished, so a mid-session occurrence reads "Running now" - not the
   * misleading "Awaiting an answer" a mid-session card used to get the instant its start time
   * passed. R25's suppression of `JoinControl`'s own "Live" chip stays right: stating it there too
   * would duplicate the header's own chip again.
   */
  it("offers the open room in both layouts and states, once, that the session is running", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2099-01-01T18:20:00Z"));
    renderCard({ ...session, place: { ...EMPTY_PLACE, joinUrl: ROOM } });

    const links = screen.getAllByRole("link", { name: "Join now" });
    expect(links).toHaveLength(2);
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      ROOM,
      ROOM,
    ]);
    expect(screen.queryByText("Live")).not.toBeInTheDocument();
    expect(screen.getByText("Running now")).toBeInTheDocument();
  });

  it("shows the action point trigger on a held session", () => {
    renderCard({ ...session, status: "HELD" }, { mode: "capture" });
    expect(
      screen.getByRole("button", { name: /\+ action point/i }),
    ).toBeInTheDocument();
  });

  it("does not show the action point trigger in contribute mode", () => {
    renderCard(session, { mode: "contribute" });
    expect(
      screen.queryByRole("button", { name: /\+ action point/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps a held session's action points on the page while it is being edited", () => {
    renderCard({ ...session, status: "HELD" }, { slug: "dojo", mode: "edit" });
    expect(
      screen.getByRole("button", { name: "+ Action point" }),
    ).toBeInTheDocument();
  });
});
