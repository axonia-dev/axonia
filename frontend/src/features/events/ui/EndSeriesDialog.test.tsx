import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useEndSeries } from "@/features/events/api/use-series";
import { EndSeriesDialog } from "@/features/events/ui/EndSeriesDialog";

vi.mock("@/features/events/api/use-series", () => ({
  useEndSeries: vi.fn(),
}));

const end = { mutate: vi.fn(), isPending: false, isError: false };
const onOpenChange = vi.fn();

function renderDialog(state: Partial<typeof end> = {}) {
  vi.mocked(useEndSeries).mockReturnValue({
    ...end,
    ...state,
  } as unknown as ReturnType<typeof useEndSeries>);
  render(
    <EndSeriesDialog
      slug="backend-guild"
      seriesId={3}
      isOpen
      onOpenChange={onOpenChange}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("EndSeriesDialog", () => {
  /** Destructive and irreversible from the organizer's side, so it is announced as a decision
   *  rather than as an ordinary dialog. */
  it("asks before ending anything", () => {
    renderDialog();

    expect(
      screen.getByRole("alertdialog", { name: "End this series?" }),
    ).toBeInTheDocument();
    expect(useEndSeries).toHaveBeenCalledWith("backend-guild");
    expect(end.mutate).not.toHaveBeenCalled();
  });

  /** What actually happens, and the half an organizer would otherwise fear: ScheduleService
   *  projects ghosts for an ACTIVE series only, so the future stops while the rows already
   *  materialised keep their place. */
  it("says the sessions already on the calendar are left alone", () => {
    renderDialog();

    expect(
      screen.getByText(/already on the\s+calendar stay where they are/),
    ).toBeInTheDocument();
  });

  it("ends the series on the confirm", async () => {
    renderDialog();

    await user.click(screen.getByRole("button", { name: "End series" }));

    expect(end.mutate).toHaveBeenCalledWith(3, expect.anything());
  }, 15_000);

  it("leaves the series alone on the way out", async () => {
    renderDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(end.mutate).not.toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  }, 15_000);

  /** A refused transition has to leave the dialog open and say so: closed on the press, an
   *  organizer would be told a series had stopped while it went on producing occurrences. */
  it("stays open and reports a refusal", () => {
    renderDialog({ isError: true });

    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
