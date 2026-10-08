import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SessionFacts } from "@/features/events/ui/SessionFacts";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

const session = aScheduledSession({
  scheduledAt: "2026-08-15T18:00:00Z",
  seriesTimeZone: "Europe/Paris",
  durationMinutes: 60,
  sections: [
    aSection({
      id: 1,
      slots: [
        aSlot({ id: 1, durationMinutes: 20, presenterUserIds: ["ann"] }),
        aSlot({ id: 2, durationMinutes: 10, presenterUserIds: ["bo", "ann"] }),
      ],
    }),
  ],
});

describe("SessionFacts", () => {
  it("states the hours it runs between, on the community's clock", () => {
    render(<SessionFacts session={session} phase="upcoming" />);
    expect(screen.getByText("20:00 to 21:00")).toBeInTheDocument();
  });

  it("times an upcoming session by what was booked, not by what its agenda adds up to today", () => {
    // The section's own slots only add up to 30 minutes; the booking says 60. An upcoming
    // session has not yet had the chance to run short, so the booking wins.
    render(<SessionFacts session={session} phase="upcoming" />);
    expect(screen.queryByText("20:00 to 20:30")).not.toBeInTheDocument();
  });

  it("times a held session by what its agenda actually ran, not by what it was booked for", () => {
    const held = { ...session, status: "HELD" as const };
    render(<SessionFacts session={held} phase="held" />);
    expect(screen.getByText("20:00 to 20:30")).toBeInTheDocument();
    expect(screen.queryByText("20:00 to 21:00")).not.toBeInTheDocument();
  });

  it("counts the talks and the distinct people giving them", () => {
    render(<SessionFacts session={session} phase="upcoming" />);
    expect(screen.getByText("2 talks")).toBeInTheDocument();
    expect(screen.getByText("2 presenters")).toBeInTheDocument();
  });

  it("says a session is still to be timed rather than inventing an hour", () => {
    render(
      <SessionFacts
        session={{ ...session, hasTime: false }}
        phase="upcoming"
      />,
    );
    expect(screen.getByText("Time to be confirmed")).toBeInTheDocument();
    expect(screen.queryByText(/20:00/)).not.toBeInTheDocument();
  });

  it("counts open spots while a session can still be contributed to", () => {
    const withOpen = {
      ...session,
      sections: [aSection({ id: 1, slots: [aSlot({ presenterUserIds: [] })] })],
    };
    render(<SessionFacts session={withOpen} phase="upcoming" />);
    expect(screen.getByText("1 open spot")).toBeInTheDocument();
  });

  it("says nothing about open spots on a session that already happened", () => {
    const withOpen = {
      ...session,
      status: "HELD" as const,
      sections: [aSection({ id: 1, slots: [aSlot({ presenterUserIds: [] })] })],
    };
    render(<SessionFacts session={withOpen} phase="held" />);
    expect(screen.queryByText(/open spot/)).not.toBeInTheDocument();
  });

  // The strip states facts and knows nothing about outgoing mail: what a release says, and who
  // may be told it at all, belongs to the chip its caller hands in.
  it("carries the release its caller hands it, after the facts it owns", () => {
    render(
      <SessionFacts
        session={session}
        phase="held"
        release={<span>Wrap-up sent</span>}
      />,
    );
    expect(screen.getByText("Wrap-up sent")).toBeInTheDocument();
  });

  it("spends no divider on a release nobody handed it", () => {
    const { container } = render(
      <SessionFacts session={session} phase="held" />,
    );
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(3);
  });

  it("says nothing about a place for a session that has not got one", () => {
    const { container } = render(
      <SessionFacts session={session} phase="upcoming" />,
    );
    expect(container.querySelectorAll('[aria-hidden="true"]')).toHaveLength(3);
  });

  it("states the same end time a round table's budget gives the run sheet", () => {
    const withRoundTable = aScheduledSession({
      scheduledAt: "2026-08-15T18:00:00Z",
      seriesTimeZone: "Europe/Paris",
      status: "HELD",
      durationMinutes: 60,
      sections: [
        aSection({
          id: 1,
          sectionType: "Ice Breaker",
          slots: [aSlot({ id: 1, durationMinutes: 8 })],
        }),
        aSection({
          id: 2,
          sectionType: "Lightning Talk",
          slots: [aSlot({ id: 2, durationMinutes: 12 })],
        }),
        aSection({
          id: 3,
          sectionType: "Deep Dive",
          slots: [
            aSlot({ id: 3, durationMinutes: 25 }),
            aSlot({ id: 4, durationMinutes: 10 }),
          ],
        }),
        aSection({
          id: 4,
          sectionType: "Round Table",
          durationMinutes: 15,
          slots: [],
        }),
        aSection({
          id: 5,
          sectionType: "Q&A",
          slots: [aSlot({ id: 5, durationMinutes: 12 })],
        }),
      ],
    });
    render(<SessionFacts session={withRoundTable} phase="held" />);
    expect(screen.getByText("20:00 to 21:22")).toBeInTheDocument();
    expect(screen.getByText("22 min over")).toBeInTheDocument();
  });

  it("gives the recording its own fact when one is handed in", () => {
    render(
      <SessionFacts
        session={session}
        phase="held"
        recording={<span>Recording</span>}
      />,
    );
    expect(screen.getByText("Recording")).toBeInTheDocument();
  });
});
