import { render, screen } from "@testing-library/react";
import { useState } from "react";
import { I18nProvider } from "react-aria-components";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCancelSession,
  useSession,
} from "@/features/events/api/use-session";
import {
  useSkipOccurrence,
  useUnskipOccurrence,
} from "@/features/events/api/use-sessions";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { useCalendarReschedule } from "@/features/events/ui/CalendarDnd";
import type { EntryEditor } from "@/features/events/ui/ScheduleEntryMenu";
import { ScheduleEntryMenu } from "@/features/events/ui/ScheduleEntryMenu";
import { aScheduledSession, aScheduleEntry } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/ui/CalendarDnd", () => ({
  useCalendarReschedule: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useSkipOccurrence: vi.fn(),
  useUnskipOccurrence: vi.fn(),
}));
vi.mock("@/features/events/api/use-session", () => ({
  useCancelSession: vi.fn(),
  useSession: vi.fn(),
}));

const reschedule = { mutate: vi.fn(), isPending: false, isError: false };

/** An occurrence of a series, and the row behind it. With a row to read the Reschedule item is
 *  within reach, so what these cases inspect is the full list rather than a curtailed one. */
const realSeriesEntry = aScheduleEntry();
const realSession = aScheduledSession({
  id: 7,
  seriesId: 1,
  occurrenceDate: "2026-07-02",
  scheduledAt: "2026-07-02T18:00:00Z",
});

function renderChipMenu(
  entry: ScheduleEntry,
  canManage = true,
  label = "Frontend Dojo",
) {
  function Host() {
    const [openEditor, setOpenEditor] = useState<EntryEditor | null>(null);
    return (
      <ScheduleEntryMenu
        slug="backend-guild"
        entry={entry}
        label={label}
        canManage={canManage}
        openEditor={openEditor}
        onOpenEditor={setOpenEditor}
        onClose={vi.fn()}
      />
    );
  }
  render(
    <I18nProvider locale="en-US">
      <Host />
    </I18nProvider>,
  );
}

/** Before the fixture occurrence, so the entry under test is one the clock has not passed: what
 *  the menu offers for a single session turns entirely on that. */
beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-07-01T12:00:00Z"));
  vi.clearAllMocks();
  vi.mocked(useCalendarReschedule).mockReturnValue(reschedule as never);
  vi.mocked(useSkipOccurrence).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useUnskipOccurrence).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useCancelSession).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useSession).mockReturnValue({ data: realSession } as never);
});

/** The shape of the list an organizer reads: its semantics, how far each run of commands reaches,
 *  and what it says it was opened on. What each command DOES on the press is the commands suite's,
 *  and what the reschedule editor is seeded from is the reschedule suite's. */
describe("ScheduleEntryMenu: how the list reads", () => {
  it("uses the shared menu semantics rather than a hand-rolled button", () => {
    renderChipMenu(realSeriesEntry);

    const items = screen.getAllByRole("menuitem");

    expect(items).toHaveLength(4);
    expect(items[0]).toHaveAccessibleName("Reschedule");
    expect(items[1]).toHaveAccessibleName("Skip");
    expect(items[2]).toHaveAccessibleName("Edit");
    expect(items[3]).toHaveAccessibleName("End");
    expect(items[0]?.querySelector("svg")).not.toBeNull();
  });

  /**
   * Two of these commands touch one date and two rewrite every date the series has. The list
   * used to carry that difference in the word "series" on the last two labels, which a reader
   * had to notice and interpret before knowing which one was safe.
   */
  it("groups the commands by how far each one reaches", () => {
    renderChipMenu(realSeriesEntry);

    const occurrence = screen.getByRole("group", { name: "This session" });
    const series = screen.getByRole("group", { name: "Whole series" });

    expect(occurrence).toContainElement(
      screen.getByRole("menuitem", { name: "Reschedule" }),
    );
    expect(occurrence).toContainElement(
      screen.getByRole("menuitem", { name: "Skip" }),
    );
    expect(series).toContainElement(
      screen.getByRole("menuitem", { name: "Edit" }),
    );
    expect(series).toContainElement(
      screen.getByRole("menuitem", { name: "End" }),
    );
  });

  /** The heading carries the noun, so the verb does not have to. */
  it("shortens the series verbs now that the heading states their reach", () => {
    renderChipMenu(realSeriesEntry);

    expect(screen.queryByRole("menuitem", { name: "Edit series" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "End series" })).toBeNull();
  });

  /**
   * A one-off belongs to no series, so there is no second reach to name.
   *
   * Counted as well as named: the group is what carries the reach now that the verbs are bare, so
   * a regression emitting Edit and End while dropping the heading would leave two commands on
   * screen with nothing saying they rewrite every date. Asserting the group's absence alone would
   * pass straight through that.
   */
  it("offers a one-off no series group at all", () => {
    renderChipMenu(
      aScheduleEntry({ seriesId: null, occurrenceDate: null, sessionId: 7 }),
    );

    expect(screen.queryByRole("group", { name: "Whole series" })).toBeNull();
    expect(screen.getAllByRole("menuitem")).toHaveLength(2);
    expect(
      screen.getByRole("group", { name: "This session" }),
    ).toBeInTheDocument();
  });

  /**
   * The menu opens over the chip that summoned it. On a grid where a weekly series puts the same
   * label on four chips, the popover covering its own target left nothing on screen saying which
   * of them the commands were about.
   *
   * The fixture occurrence is 2 July, and the suite's clock is pinned west of UTC, so a header
   * composed on the viewer's frame would read 1 July here and pass nowhere.
   */
  it("names the occurrence it was opened on, read as a calendar day", () => {
    renderChipMenu(realSeriesEntry, true, "Frontend Dojo");

    expect(screen.getByText("Frontend Dojo")).toBeInTheDocument();
    expect(screen.getByText(/Thu, Jul 2/)).toBeInTheDocument();
  });

  /**
   * A one-off is anchored to no series and therefore carries no `occurrenceDate` at all: only a
   * series occurrence has one. Composed from that field alone, its header stated a bare hour and
   * left the day off the one menu whose whole job is saying which session it acts on.
   */
  it("names the day of a one-off, which has no occurrence anchor to read", () => {
    renderChipMenu(
      aScheduleEntry({
        seriesId: null,
        occurrenceDate: null,
        scheduledAt: "2026-07-02T18:00:00Z",
      }),
    );

    expect(screen.getByText(/Thu, Jul 2/)).toBeInTheDocument();
  });

  /**
   * A held occurrence of a series offers nothing for the date itself, so the first group drops
   * out and the second is all that is left. Bare, an Edit and an End would then say nothing at
   * all about rewriting every date the series has.
   */
  it("keeps the surviving group's heading when the first one drops out", () => {
    renderChipMenu(aScheduleEntry({ status: "HELD" }));

    expect(screen.queryByRole("group", { name: "This session" })).toBeNull();
    expect(screen.getAllByRole("menuitem")).toHaveLength(2);
    expect(
      screen.getByRole("group", { name: "Whole series" }),
    ).toContainElement(screen.getByRole("menuitem", { name: "Edit" }));
  });

  it("offers no chip actions without canManage", () => {
    renderChipMenu(realSeriesEntry, false);

    expect(
      screen.queryByRole("menuitem", { name: /reschedule/i }),
    ).not.toBeInTheDocument();
    // A menu named for the occurrence, holding a note, not a stray line of prose outside any menu.
    expect(
      screen.getByRole("menu", { name: /^Frontend Dojo, Thu, Jul 2/ }),
    ).toBeInTheDocument();
    expect(screen.getByText(/no actions/i)).toBeInTheDocument();
  });
});
