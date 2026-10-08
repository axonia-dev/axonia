import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  type ContextTarget,
  ScheduleContextMenu,
} from "@/features/events/ui/ScheduleContextMenu";
import { aScheduleEntry } from "@/test/session-fixture";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

/** Stands in for the entry menu so this suite is about the container alone: the day items it holds,
 *  and when it steps aside. What the entry menu itself offers has its own suite. */
vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/ui/ScheduleEntryMenu", () => ({
  ScheduleEntryMenu: ({
    onOpenEditor,
  }: Readonly<{ onOpenEditor: (editor: "reschedule" | "series") => void }>) => (
    <button type="button" onClick={() => onOpenEditor("reschedule")}>
      open editor
    </button>
  ),
}));

const chipEntry = aScheduleEntry();

function renderMenu(
  target: ContextTarget,
  opts: {
    canStartSeries?: boolean;
    onCreateSeries?: (iso: string) => void;
    onCreateOneOff?: (iso: string) => void;
  } = {},
) {
  const onClose = vi.fn();
  render(
    <ScheduleContextMenu
      slug="backend-guild"
      target={target}
      x={10}
      y={20}
      canManage={false}
      canStartSeries={opts.canStartSeries ?? false}
      onClose={onClose}
      {...(opts.onCreateSeries !== undefined && {
        onCreateSeries: opts.onCreateSeries,
      })}
      {...(opts.onCreateOneOff !== undefined && {
        onCreateOneOff: opts.onCreateOneOff,
      })}
    />,
  );
  return { onClose };
}

describe("ScheduleContextMenu", () => {
  describe("day menu", () => {
    it("shows two action items when canStartSeries is true", () => {
      renderMenu({ kind: "day", iso: "2026-07-05" }, { canStartSeries: true });
      expect(
        screen.getByRole("menuitem", { name: /new series/i }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("menuitem", { name: /one-off session/i }),
      ).toBeInTheDocument();
    });

    it("calls onCreateSeries with the ISO date and calls onClose when 'New series' is clicked", async () => {
      const onCreateSeries = vi.fn();
      const { onClose } = renderMenu(
        { kind: "day", iso: "2026-07-05" },
        { canStartSeries: true, onCreateSeries },
      );
      await user.click(screen.getByRole("menuitem", { name: /new series/i }));
      expect(onCreateSeries).toHaveBeenCalledWith("2026-07-05");
      expect(onClose).toHaveBeenCalled();
    });

    it("calls onCreateOneOff with the ISO date and calls onClose when 'One-off session' is clicked", async () => {
      const onCreateOneOff = vi.fn();
      const { onClose } = renderMenu(
        { kind: "day", iso: "2026-07-05" },
        { canStartSeries: true, onCreateOneOff },
      );
      await user.click(
        screen.getByRole("menuitem", { name: /one-off session/i }),
      );
      expect(onCreateOneOff).toHaveBeenCalledWith("2026-07-05");
      expect(onClose).toHaveBeenCalled();
    });

    it("renders no form inside the menu (no Title or Date inputs)", () => {
      renderMenu({ kind: "day", iso: "2026-07-05" }, { canStartSeries: true });
      expect(screen.queryByLabelText(/title/i)).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/^date$/i)).not.toBeInTheDocument();
    });

    it("offers no day actions without canStartSeries", () => {
      renderMenu({ kind: "day", iso: "2026-07-05" }, { canStartSeries: false });
      expect(
        screen.queryByRole("menuitem", { name: /new series/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("menuitem", { name: /one-off session/i }),
      ).not.toBeInTheDocument();
      expect(
        screen.getByRole("menu", { name: "Sun, Jul 5" }),
      ).toBeInTheDocument();
      expect(screen.getByText(/no actions/i)).toBeInTheDocument();
    });

    /** The menu opens over the cell it was summoned from, so it states the day rather than
     *  relying on a reader remembering where the pointer was. Read in UTC: the suite's clock is
     *  west of UTC and a local reading would name 4 July. */
    it("names the day it was opened on", () => {
      renderMenu({ kind: "day", iso: "2026-07-05" }, { canStartSeries: true });

      expect(screen.getByText(/Sun, Jul 5/)).toBeInTheDocument();
    });

    it("drops the 'here' the stated day already carries", () => {
      renderMenu({ kind: "day", iso: "2026-07-05" }, { canStartSeries: true });

      expect(
        screen.getByRole("menuitem", { name: "New series" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("menuitem", { name: "One-off session" }),
      ).toBeInTheDocument();
    });
  });

  /**
   * The menu owns the editor it opens, so it cannot close itself to get out of the way: unmounting
   * would take the editor down with it, mid-edit. It steps out of sight instead, and the dialog
   * that replaced the old in-menu date field is the only thing left on screen.
   */
  it("steps out of sight while the editor it opens is up, without unmounting it", async () => {
    renderMenu({ kind: "entry", entry: chipEntry, label: "Frontend Dojo" });

    const editor = screen.getByRole("button", { name: "open editor" });
    expect(editor.parentElement).not.toHaveClass("invisible");

    await user.click(editor);

    expect(
      screen.getByRole("button", { name: "open editor" }).parentElement,
    ).toHaveClass("invisible");
  });
});
