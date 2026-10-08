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
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Session } from "@/features/events/domain/session";
import { useCalendarReschedule } from "@/features/events/ui/CalendarDnd";
import type { EntryEditor } from "@/features/events/ui/ScheduleEntryMenu";
import { ScheduleEntryMenu } from "@/features/events/ui/ScheduleEntryMenu";
import {
  A_PLACE,
  aScheduledSession,
  aScheduleEntry,
} from "@/test/session-fixture";

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
const skip = { mutate: vi.fn() };
const unskip = { mutate: vi.fn() };
const cancel = { mutate: vi.fn() };

/** The occurrence's own place, distinct from the series' standing one below, so a target seeded
 *  from the projection cannot pass by agreeing with one seeded from the row. */
const OWN_PLACE = {
  location: "The annex",
  joinUrl: "https://meet.example/this-week",
  joiningDetails: "Passcode 1111",
};

/**
 * A chip for an occurrence that already has a row. Its `place` is the RESOLVED one - all the
 * schedule projection carries - so it reads as the occurrence's own place while saying nothing
 * about whether the occurrence overrides or inherits.
 */
const realSeriesEntry = aScheduleEntry({ place: OWN_PLACE });

/** The same occurrence as the server holds it. Neither fact appears on the chip. */
const realSession = aScheduledSession({
  id: 7,
  seriesId: 1,
  occurrenceDate: "2026-07-02",
  scheduledAt: "2026-07-02T18:00:00Z",
  facilitatorUserId: "alice",
  place: OWN_PLACE,
  placeOverride: OWN_PLACE,
  seriesPlace: A_PLACE,
});

/** The same series a week on, still only projected: no row, so no override and no facilitator. */
const ghostEntry = aScheduleEntry({
  sessionId: null,
  seriesId: 5,
  occurrenceDate: "2026-07-09",
  scheduledAt: "2026-07-09T18:00:00Z",
  virtual: true,
});

/** The same occurrence inheriting instead: a resolved `place` over an empty `placeOverride` is
 *  the only shape that tells a target seeded from the override apart from one seeded from it. */
const inheritingSession = aScheduledSession({
  ...realSession,
  place: A_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: A_PLACE,
});

/** What the row read comes back as: the row, nothing yet, or a read that failed. */
function rowReads(state: { data?: Session; isError?: boolean }) {
  vi.mocked(useSession).mockReturnValue(state as never);
}

function renderChipMenu(entry: ScheduleEntry, canManage = true) {
  // Stepping aside is the container's move, made on this signal alone.
  const onReschedule = vi.fn();
  function Host() {
    const [openEditor, setOpenEditor] = useState<EntryEditor | null>(null);
    return (
      <ScheduleEntryMenu
        slug="backend-guild"
        entry={entry}
        label="Frontend Dojo"
        canManage={canManage}
        openEditor={openEditor}
        onOpenEditor={(editor) => {
          if (editor === "reschedule") onReschedule();
          setOpenEditor(editor);
        }}
        onClose={vi.fn()}
      />
    );
  }
  render(
    <I18nProvider locale="en-US">
      <Host />
    </I18nProvider>,
  );
  return { onReschedule };
}

function confirmed() {
  return reschedule.mutate.mock.calls[0]?.[0] as {
    entry: ScheduleEntry;
    input: UpdateSessionInput;
  };
}

let user: ReturnType<typeof userEvent.setup>;

/** Before the fixture occurrence: Skip is offered only on one the clock has not passed, and one
 *  case here presses it. */
beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-07-01T12:00:00Z"));
  vi.clearAllMocks();
  vi.mocked(useCalendarReschedule).mockReturnValue(reschedule as never);
  vi.mocked(useSkipOccurrence).mockReturnValue(skip as never);
  vi.mocked(useUnskipOccurrence).mockReturnValue(unskip as never);
  vi.mocked(useCancelSession).mockReturnValue(cancel as never);
  rowReads({ data: realSession });
});

/** The entry menu's reschedule editor, and the one question it turns on: what the editor is
 *  seeded from. Split from the menu's own suite, which is about what it offers and how each
 *  item reads. */
describe("ScheduleEntryMenu: what the reschedule editor is seeded from", () => {
  /**
   * The regression the branch on `entry.sessionId` exists to stop. `PUT /sessions/{id}` is a full
   * replacement, and the schedule projection carries neither the facilitator nor the difference
   * between an overridden place and an inherited one. Seeded from the chip, this gesture posted an
   * empty place and a null facilitator: the room set for this one occurrence reverted to the
   * series', whoever was running it was wiped, and the dialog showed the occurrence's own place
   * under a line calling it the series' standing one.
   */
  it("keeps a real occurrence's own place and its facilitator across a reschedule", async () => {
    renderChipMenu(realSeriesEntry);
    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));

    // Seeded from the row: the override, not the projection's resolved place and not a blank.
    expect(screen.getByLabelText("Join link")).toHaveValue(OWN_PLACE.joinUrl);

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    const call = confirmed();
    expect(call.entry).toBe(realSeriesEntry);
    expect(call.input.place).toEqual(OWN_PLACE);
    expect(call.input.facilitatorUserId).toBe("alice");
    expect(call.input.durationMinutes).toBe(60);
  }, 15_000);

  /** The chip equivalent of the drag's inheriting case: seeded from the resolved place, a
   *  confirm would stamp the series' place onto this occurrence and detach it for good. */
  it("leaves an inheriting occurrence inheriting after a reschedule", async () => {
    rowReads({ data: inheritingSession });
    renderChipMenu(realSeriesEntry);
    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));

    expect(screen.getByLabelText("Join link")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    expect(confirmed().input.place).toEqual(EMPTY_PLACE);
  }, 15_000);

  /** A read can fail, and the row is the only thing the editor can be seeded from. Left ungated,
   *  the item stayed live on a failure: choosing it took the menu off screen and put nothing in
   *  its place, with no explanation and nothing left to click. */
  it("keeps the rest of the menu working, and says why, when the row cannot be read", async () => {
    rowReads({ isError: true });
    const { onReschedule } = renderChipMenu(realSeriesEntry);

    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));

    expect(onReschedule).not.toHaveBeenCalled();
    expect(
      screen.getByText("This session could not be read."),
    ).toBeInTheDocument();

    // Skip and Cancel need no row, so a failed read costs the organizer neither.
    await user.click(screen.getByRole("menuitem", { name: /^skip$/i }));
    expect(skip.mutate).toHaveBeenCalledOnce();
  }, 15_000);

  it("holds the menu in place while the row is still being read", async () => {
    rowReads({});
    const { onReschedule } = renderChipMenu(realSeriesEntry);

    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));

    expect(onReschedule).not.toHaveBeenCalled();
    expect(
      screen.getByRole("menuitem", { name: "Reschedule" }),
    ).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByLabelText("Join link")).not.toBeInTheDocument();
  }, 15_000);

  /** A ghost has no row to read and neither fact to lose. */
  it("reschedules a ghost from what the calendar projects, without reading a row", async () => {
    rowReads({});
    renderChipMenu(ghostEntry);

    await user.click(screen.getByRole("menuitem", { name: "Reschedule" }));
    expect(vi.mocked(useSession)).toHaveBeenCalledWith(null);
    expect(reschedule.mutate).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: /confirm/i }));

    const call = confirmed();
    expect(call.entry).toBe(ghostEntry);
    expect(call.input.place).toEqual(EMPTY_PLACE);
    expect(call.input.facilitatorUserId).toBeNull();
  }, 15_000);
});
