import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Memory } from "@/features/events/domain/memory";
import { NO_TRACES } from "@/features/events/domain/memory";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Slot } from "@/features/events/domain/slot";
import { MemoryCard } from "@/features/events/ui/timeline/MemoryCard";
import { accessibleName } from "@/features/events/ui/timeline/timeline";
import { morphNames } from "@/test/morph-names";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));

function slot(id: number, presenters: string[]): Slot {
  return {
    id,
    sectionId: 1,
    position: id,
    presenterUserIds: presenters,
    topicId: null,
    title: null,
    notes: null,
    durationMinutes: 15,
    tags: [],
  };
}

function entry(overrides: Partial<ScheduleEntry> = {}): ScheduleEntry {
  return {
    sessionId: 7,
    seriesId: 1,
    title: null,
    occurrenceDate: "2026-01-02",
    scheduledAt: "2026-01-02T18:00:00Z",
    hasTime: true,
    seriesTimeZone: "Europe/Paris",
    durationMinutes: 60,
    status: "HELD",
    virtual: false,
    place: EMPTY_PLACE,
    sections: [],
    ...overrides,
  };
}

function setup(memory: Memory, canManage = false) {
  const { container } = render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <MemoryCard
          memory={memory}
          label="Frontend Dojo"
          href="/s/7"
          canManage={canManage}
          fade={0.8}
        />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return container;
}

describe("MemoryCard", () => {
  it("shows each trace with its count spoken", () => {
    // when
    setup({
      entry: entry(),
      traces: { loves: 9, recorded: true, artifacts: 2 },
    });
    // then
    expect(screen.getByText("9")).toBeInTheDocument();
    expect(screen.getByText("9 loves")).toBeInTheDocument();
    expect(screen.getAllByText("Recording").length).toBeGreaterThan(0);
    expect(screen.getAllByText("2 artifacts").length).toBeGreaterThan(0);
  });

  it("hides every trace at zero", () => {
    // when
    setup({ entry: entry(), traces: NO_TRACES });
    // then
    expect(screen.queryByText(/love/)).not.toBeInTheDocument();
    expect(screen.queryByText("Recording")).not.toBeInTheDocument();
    expect(screen.queryByText(/artifact/)).not.toBeInTheDocument();
  });

  it("uses the singular for one love and one artifact", () => {
    // when
    setup({
      entry: entry(),
      traces: { loves: 1, recorded: false, artifacts: 1 },
    });
    // then
    expect(screen.getByText("1 love")).toBeInTheDocument();
    expect(screen.getAllByText("1 artifact").length).toBeGreaterThan(0);
  });

  it("counts filled slots as talks", () => {
    // given
    const sections = [
      {
        id: 1,
        sessionId: 7,
        position: 0,
        sectionTypeId: null,
        sectionType: null,
        sectionTypeIcon: null,
        sectionTypeAccentColor: null,
        title: null,
        durationMinutes: null,
        slots: [slot(1, ["u1"]), slot(2, ["u2"]), slot(3, [])],
      },
    ];
    // when
    setup({ entry: entry({ sections }), traces: NO_TRACES });
    // then
    expect(screen.getByText(/2 talks/)).toBeInTheDocument();
  });

  it("says 1 talk for a single filled slot", () => {
    // given
    const sections = [
      {
        id: 1,
        sessionId: 7,
        position: 0,
        sectionTypeId: null,
        sectionType: null,
        sectionTypeIcon: null,
        sectionTypeAccentColor: null,
        title: null,
        durationMinutes: null,
        slots: [slot(1, ["u1"])],
      },
    ];
    // when
    setup({ entry: entry({ sections }), traces: NO_TRACES });
    // then
    expect(screen.getByText(/1 talk$/)).toBeInTheDocument();
  });

  it("hides the awaiting chip and traces from a reader on a scheduled session", () => {
    // given
    const memory = {
      entry: entry({ status: "SCHEDULED" as const }),
      traces: { loves: 3, recorded: true, artifacts: 1 },
    };
    // when
    setup(memory);
    // then
    expect(screen.queryByText("Awaiting an answer")).not.toBeInTheDocument();
    expect(screen.queryByText("3 loves")).not.toBeInTheDocument();
  });

  it("shows the awaiting chip instead of traces for a manager", () => {
    // when
    setup({ entry: entry({ status: "SCHEDULED" }), traces: NO_TRACES }, true);
    // then
    expect(screen.getByText("Awaiting an answer")).toBeInTheDocument();
  });

  it("speaks the traces after the card's name", () => {
    // given
    const e = entry();
    // when
    setup({ entry: e, traces: { loves: 9, recorded: true, artifacts: 2 } });
    // then
    expect(
      screen.getByRole("link", {
        name: `${accessibleName(e, "Frontend Dojo")}, 9 loves, recording, 2 artifacts`,
      }),
    ).toBeInTheDocument();
  });

  it("speaks only the traces that are there", () => {
    // when
    setup({
      entry: entry(),
      traces: { loves: 1, recorded: false, artifacts: 0 },
    });
    // then
    expect(screen.getByRole("link")).toHaveAccessibleName(
      `${accessibleName(entry(), "Frontend Dojo")}, 1 love`,
    );
  });

  it("speaks no traces while the session awaits an answer", () => {
    // given
    const awaiting = entry({ status: "SCHEDULED" });
    // when
    setup({
      entry: awaiting,
      traces: { loves: 3, recorded: true, artifacts: 1 },
    });
    // then
    expect(screen.getByRole("link")).toHaveAccessibleName(
      accessibleName(awaiting, "Frontend Dojo"),
    );
  });

  it("links to the session with the full accessible name", () => {
    // given
    const e = entry();
    // when
    setup({ entry: e, traces: NO_TRACES });
    // then
    const link = screen.getByRole("link", {
      name: accessibleName(e, "Frontend Dojo"),
    });
    expect(link).toHaveAttribute("href", "/s/7");
  });

  it("names the surface and title like a timeline card", () => {
    // when
    const container = setup({
      entry: entry({ title: "Frontend Dojo" }),
      traces: NO_TRACES,
    });
    // then
    expect(morphNames(container)).toEqual([
      "morph-surface-session-7",
      "morph-title-session-7",
    ]);
  });
});
