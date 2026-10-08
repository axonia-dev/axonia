import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { I18nProvider } from "react-aria-components";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMemberZones } from "@/features/events/api/use-member-zones";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { RescheduleTarget } from "@/features/events/domain/reschedule-target";
import { RescheduleDialog } from "@/features/events/ui/RescheduleDialog";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

/**
 * The suite pins the viewer to America/Los_Angeles and every fixture here meets in Europe/Paris,
 * so a dialog that read the viewer's clock instead of the series' would compose an instant nine
 * hours away rather than one that happens to agree.
 */
function target(overrides: Partial<RescheduleTarget> = {}): RescheduleTarget {
  return {
    scheduledAt: "2026-08-13T15:00:00Z",
    hasTime: true,
    durationMinutes: 60,
    facilitatorUserId: "alice",
    placeOverride: EMPTY_PLACE,
    seriesPlace: EMPTY_PLACE,
    seriesTimeZone: "Europe/Paris",
    ...overrides,
  };
}

function renderDialog(props: {
  target: RescheduleTarget;
  onConfirm: (input: UpdateSessionInput) => void;
  isError?: boolean;
}) {
  return render(
    <I18nProvider locale="en-US">
      <RescheduleDialog
        slug="platform"
        isOpen
        onOpenChange={vi.fn()}
        target={props.target}
        isPending={false}
        isError={props.isError ?? false}
        onConfirm={props.onConfirm}
      />
    </I18nProvider>,
  );
}

function confirmedInput(
  onConfirm: ReturnType<typeof vi.fn>,
): UpdateSessionInput {
  const [input] = onConfirm.mock.calls[0] as [UpdateSessionInput];
  return input;
}

describe("RescheduleDialog (the spread of the roster)", () => {
  /** 2026-08-13T15:00Z is 17:00 in Paris, the zone that governs this occurrence, and reads as
   *  08:00 in Los Angeles and 20:30 in Kolkata. */
  it("projects the occurrence's governing clock, not the viewer's", () => {
    vi.mocked(useMemberZones).mockReturnValue({
      data: [
        { zone: "Asia/Kolkata", count: 4 },
        { zone: "America/Los_Angeles", count: 2 },
      ],
    } as never);

    renderDialog({ target: target(), onConfirm: vi.fn() });

    expect(
      screen.getByText("08:00 in Los Angeles to 21:30 in Kolkata"),
    ).toBeVisible();
  });
});

describe("RescheduleDialog", () => {
  it("composes the typed time in the series' zone, not the viewer's", async () => {
    const onConfirm = vi.fn();
    renderDialog({
      target: target({
        seriesTimeZone: "Europe/Paris",
        scheduledAt: "2026-08-13T15:00:00Z",
        hasTime: true,
      }),
      onConfirm,
    });

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(onConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        scheduledAt: {
          date: "2026-08-13",
          time: "17:00",
          zone: "Europe/Paris",
        },
      }),
    );
  }, 15_000);

  it("names the zone the typed time is read in", () => {
    renderDialog({ target: target(), onConfirm: vi.fn() });

    expect(screen.getByText("Paris")).toBeInTheDocument();
  });

  /** The fact the two fields cannot show: what the same moment reads as at home. */
  it("says what the typed time comes to on the viewer's own clock", () => {
    renderDialog({ target: target(), onConfirm: vi.fn() });

    expect(screen.getByText("08:00 in Los Angeles.")).toBeInTheDocument();
  });

  /**
   * The offset the line exists to state is wide enough here to land on another day: 00:30 in Paris
   * on the 14th is the afternoon of the 13th in Los Angeles. A bare hour left that unsaid, and the
   * reader had nowhere else to learn it.
   */
  it("names the day too when the two clocks disagree about which one it is", () => {
    renderDialog({
      target: target({ scheduledAt: "2026-08-13T22:30:00Z" }),
      onConfirm: vi.fn(),
    });

    expect(
      screen.getByText("15:30 on Thu 13 Aug in Los Angeles."),
    ).toBeInTheDocument();
  });

  it("says nothing about a second clock when the series meets on the viewer's own", () => {
    renderDialog({
      target: target({ seriesTimeZone: "America/Los_Angeles" }),
      onConfirm: vi.fn(),
    });

    expect(screen.queryByText(/in Los Angeles\./)).not.toBeInTheDocument();
  });

  it("hands back the facts a full replacement must not disturb", async () => {
    const onConfirm = vi.fn();
    renderDialog({ target: target(), onConfirm });

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(confirmedInput(onConfirm).durationMinutes).toBe(60);
    expect(confirmedInput(onConfirm).facilitatorUserId).toBe("alice");
  }, 15_000);

  it("seeds the place from the override so saving an inheriting occurrence does not detach it", async () => {
    const onConfirm = vi.fn();
    renderDialog({
      target: target({
        placeOverride: EMPTY_PLACE,
        seriesPlace: {
          location: null,
          joinUrl: "https://meet.example/dojo",
          joiningDetails: null,
        },
      }),
      onConfirm,
    });

    expect(screen.getByLabelText("Join link")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(confirmedInput(onConfirm).place).toEqual(EMPTY_PLACE);
  }, 15_000);

  /**
   * The defect the native `datetime-local` could not express: it has no way to say "no hour", so
   * confirming an untimed occurrence through the dialog gave it one - midnight, or whatever the
   * seed rounded to - on the gesture that means "change nothing about when".
   */
  it("leaves the time empty for an untimed occurrence and confirms it as untimed", async () => {
    const onConfirm = vi.fn();
    renderDialog({ target: target({ hasTime: false }), onConfirm });

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(onConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        scheduledAt: expect.objectContaining({ time: null }) as unknown,
      }),
    );
  }, 15_000);

  it("says an empty hour is an answer rather than an omission", () => {
    renderDialog({ target: target({ hasTime: false }), onConfirm: vi.fn() });

    expect(
      screen.getByText("Leave empty to keep the hour open."),
    ).toBeInTheDocument();
  });

  /** A one-off states its own zone, and the server refuses one with no time, so it may not. */
  it("refuses to send a one-off with no hour at all", () => {
    renderDialog({
      target: target({ hasTime: false, seriesTimeZone: null }),
      onConfirm: vi.fn(),
    });

    expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
  });
});

/**
 * Closing is the caller's signal that the write landed: it calls `onOpenChange(false)` from its
 * own `onSuccess` and nowhere else. A refused join link - the server rejects one that does not
 * start with http - has to leave the organizer looking at what they typed, because closing throws
 * away the new date along with the bad link and says nothing at all.
 */
function RefusingHost() {
  const [isOpen, setIsOpen] = useState(true);
  const [isError, setIsError] = useState(false);
  return (
    <I18nProvider locale="en-US">
      <RescheduleDialog
        slug="platform"
        isOpen={isOpen}
        onOpenChange={setIsOpen}
        target={target()}
        isPending={false}
        isError={isError}
        onConfirm={() => setIsError(true)}
      />
    </I18nProvider>
  );
}

describe("RescheduleDialog - a refused write", () => {
  it("keeps the dialog open with the typed values and the refusal on screen", async () => {
    render(<RefusingHost />);
    await user.type(
      screen.getByLabelText("Join link"),
      "meet.example/no-scheme",
    );

    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("Join link")).toHaveValue(
      "meet.example/no-scheme",
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
  }, 15_000);
});
