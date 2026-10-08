import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { ZonedParts } from "@/features/events/domain/zoned-instant";
import { ReleaseScheduleDialog } from "@/features/events/ui/ReleaseScheduleDialog";

function renderDialog(props: {
  value: ZonedParts;
  latestReleaseAt?: string;
  onConfirm?: (releaseAt: string) => void;
}) {
  return render(
    <ReleaseScheduleDialog
      title="Schedule the reminder"
      fieldLabel="Reminder release date and time"
      isOpen
      onOpenChange={vi.fn()}
      value={props.value}
      onValueChange={vi.fn()}
      hasCurrentSchedule
      onConfirm={props.onConfirm ?? vi.fn()}
      isPending={false}
      {...(props.latestReleaseAt === undefined
        ? {}
        : { latestReleaseAt: props.latestReleaseAt })}
    />,
  );
}

/**
 * `value` is the caller's state, not the fields': the segmented fields drop anything they cannot
 * parse on the way in, so a value no clock can read cannot be provoked by typing. The only way in
 * is the prop, and the only thing standing between it and the bound check that runs on every
 * render is the guard inside it.
 */
describe("ReleaseScheduleDialog - a value no clock can read", () => {
  it("keeps rendering rather than taking the dialog down with it", () => {
    // Month 13. Not February 31st: `new Date` rolls an out-of-range DAY over into the next
    // month and parses happily, so only an out-of-range field is rejected outright.
    renderDialog({
      value: { date: "2026-13-01", time: "10:00" },
      latestReleaseAt: "2026-03-14T18:00:00Z",
    });

    expect(
      screen.getByRole("dialog", { name: "Schedule the reminder" }),
    ).toBeInTheDocument();
    // Unreadable is not the same as out of range: the fields are not marked invalid, and the
    // bound check simply has nothing to say about a value it cannot read.
    expect(
      screen.getByRole("spinbutton", { name: /month/i }),
    ).not.toHaveAttribute("aria-invalid", "true");
    expect(
      screen.getByLabelText("Reminder release date and time"),
    ).not.toHaveAccessibleDescription();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("refuses to send it, on the same null that stopped the bound being checked", () => {
    renderDialog({
      value: { date: "2026-13-01", time: "10:00" },
      latestReleaseAt: "2026-03-14T18:00:00Z",
    });

    // Surviving the render is not enough on its own: a value unreadable enough to skip the
    // bound check must not also be sendable, or the guard would have bought a silent bypass
    // of the reminder's bound in exchange for the crash it prevents.
    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });

  it("refuses to send a date with no hour beside it", () => {
    renderDialog({ value: { date: "2026-03-13", time: "" } });

    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });

  it("still sends a value it can read", async () => {
    const onConfirm = vi.fn();
    renderDialog({
      value: { date: "2026-03-13", time: "15:30" },
      latestReleaseAt: "2026-03-14T18:00:00Z",
      onConfirm,
    });

    await userEvent
      .setup({ delay: null })
      .click(screen.getByRole("button", { name: "Confirm" }));

    // 2026-03-13T15:30 local (PDT, UTC-7) is 2026-03-13T22:30:00.000Z.
    expect(onConfirm).toHaveBeenCalledWith("2026-03-13T22:30:00.000Z");
  }, 15_000);

  /** The clock the picked parts are read on, named where the reader can see it. */
  it("names the viewer's own zone beside the field", () => {
    renderDialog({ value: { date: "2026-03-13", time: "15:30" } });

    expect(screen.getByText("Los Angeles")).toBeInTheDocument();
  });
});
