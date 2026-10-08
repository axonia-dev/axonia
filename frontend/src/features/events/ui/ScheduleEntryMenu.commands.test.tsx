import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
vi.mock("@/features/events/api/use-series", () => ({
  useSeriesList: vi.fn(),
  useEndSeries: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
}));
vi.mock("@/features/events/api/use-session", () => ({
  useCancelSession: vi.fn(),
  useSession: vi.fn(),
}));

const reschedule = { mutate: vi.fn(), isPending: false, isError: false };
const skip = { mutate: vi.fn() };
const unskip = { mutate: vi.fn() };
const cancel = { mutate: vi.fn() };

/** An occurrence of a series that already has a row, and the row itself. */
const realSeriesEntry = aScheduleEntry();
const realSession = aScheduledSession({
  id: 7,
  seriesId: 1,
  occurrenceDate: "2026-07-02",
  scheduledAt: "2026-07-02T18:00:00Z",
});

const skippedEntry = aScheduleEntry({
  sessionId: null,
  seriesId: 5,
  occurrenceDate: "2099-12-12",
  scheduledAt: null,
  status: "SKIPPED",
});

function renderChipMenu(entry: ScheduleEntry) {
  function Host() {
    const [openEditor, setOpenEditor] = useState<EntryEditor | null>(null);
    return (
      <ScheduleEntryMenu
        slug="backend-guild"
        entry={entry}
        label="Frontend Dojo"
        canManage
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

let user: ReturnType<typeof userEvent.setup>;

/** Before the fixture occurrence, so the entry under test is one the clock has not passed: which
 *  verb an entry is offered turns entirely on that. */
beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-07-01T12:00:00Z"));
  vi.clearAllMocks();
  vi.mocked(useCalendarReschedule).mockReturnValue(reschedule as never);
  vi.mocked(useSkipOccurrence).mockReturnValue(skip as never);
  vi.mocked(useUnskipOccurrence).mockReturnValue(unskip as never);
  vi.mocked(useCancelSession).mockReturnValue(cancel as never);
  vi.mocked(useSession).mockReturnValue({ data: realSession } as never);
});

/** Which verb is true of a given entry, and what pressing it does. How the list is shaped and
 *  grouped is the menu's own suite; what the reschedule editor is seeded from is the reschedule
 *  suite's. */
describe("ScheduleEntryMenu: the command an entry offers, and what it does", () => {
  /**
   * Calling one session off is one decision, and the menu used to offer two words for it side by
   * side. Skip and Cancel differ in ways the labels do not carry - one is reversible, the other is
   * not - so each chip now offers only the verb that is true of it.
   *
   * A future occurrence of a series can be skipped, so it is not also offered a cancel.
   */
  it("offers a future occurrence one way to call it off, and it is Skip", () => {
    renderChipMenu(realSeriesEntry);

    expect(screen.getByRole("menuitem", { name: "Skip" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Cancel" })).toBeNull();
  });

  /** A one-off belongs to no series. `POST /series/{id}/occurrences/skip` has nothing to address,
   *  and the session genuinely cannot come back, so Cancel is the honest word. */
  it("offers a one-off Cancel, since there is no series to skip an occurrence of", () => {
    renderChipMenu(
      aScheduleEntry({ seriesId: null, occurrenceDate: null, sessionId: 7 }),
    );

    expect(
      screen.getByRole("menuitem", { name: "Cancel" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Skip" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Edit" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "End" })).toBeNull();
  });

  /** The server refuses a retroactive skip outright - `OCCURRENCE_IN_PAST`, whose own message says
   *  to cancel it instead - so an item offering one there would look live and answer with a
   *  conflict. */
  it("falls back to Cancel once the clock has passed the occurrence", () => {
    vi.setSystemTime(new Date("2026-07-03T12:00:00Z"));
    renderChipMenu(realSeriesEntry);

    expect(
      screen.getByRole("menuitem", { name: "Cancel" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Skip" })).toBeNull();
  });

  it("opens the shared dialog rather than a date-only field", async () => {
    renderChipMenu(realSeriesEntry);

    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));

    expect(screen.getByLabelText("Join link")).toBeInTheDocument();
  }, 15_000);

  it("skips a series occurrence from a chip", async () => {
    renderChipMenu(realSeriesEntry);
    await user.click(screen.getByRole("menuitem", { name: /^skip$/i }));
    expect(skip.mutate).toHaveBeenCalledWith({
      seriesId: 1,
      occurrenceDate: "2026-07-02",
    });
  });

  it("cancels a one-off from a chip", async () => {
    renderChipMenu(aScheduleEntry({ seriesId: null, occurrenceDate: null }));
    await user.click(screen.getByRole("menuitem", { name: /^cancel$/i }));
    expect(cancel.mutate).toHaveBeenCalledWith(7);
  });

  it("unskips a skipped occurrence", async () => {
    renderChipMenu(skippedEntry);
    await user.click(screen.getByRole("menuitem", { name: /unskip/i }));
    expect(unskip.mutate).toHaveBeenCalledWith({
      seriesId: 5,
      occurrenceDate: "2099-12-12",
    });
  });

  /** Stopping a recurring commitment is the answer a single occurrence never was: without it an
   *  organizer had to skip each occurrence as it appeared, forever. */
  it("opens the end-series confirmation rather than ending it on the press", async () => {
    renderChipMenu(realSeriesEntry);

    await user.click(screen.getByRole("menuitem", { name: "End" }));

    expect(
      screen.getByRole("alertdialog", { name: "End this series?" }),
    ).toBeInTheDocument();
  }, 15_000);
});
