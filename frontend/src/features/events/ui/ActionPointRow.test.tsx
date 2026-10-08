import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSettleActionItem } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { ActionPointRow } from "@/features/events/ui/ActionPointRow";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/features/events/api/use-action-items", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useSettleActionItem: vi.fn(),
}));

const mutate = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSettleActionItem).mockReturnValue({
    mutate,
  } as unknown as ReturnType<typeof useSettleActionItem>);
});

function renderRow(item: ActionItem) {
  render(
    <MemoryRouter>
      <ul>
        <ActionPointRow slug="backend-guild" item={item} users={{}} />
      </ul>
    </MemoryRouter>,
  );
}

const TITLE = "Migrate the old dashboards";

describe("ActionPointRow settle control", () => {
  it("settles an open row as done", async () => {
    const user = userEvent.setup({ delay: null });
    renderRow(anActionItem({ title: TITLE, outcome: "OPEN" }));

    await user.click(
      screen.getByRole("button", { name: `Not done: ${TITLE}` }),
    );

    expect(mutate).toHaveBeenCalledWith("DONE");
  });

  it("settles a done row as dropped", async () => {
    const user = userEvent.setup({ delay: null });
    renderRow(
      anActionItem({
        title: TITLE,
        outcome: "DONE",
        canClose: false,
        canReopen: true,
      }),
    );

    await user.click(screen.getByRole("button", { name: `Done: ${TITLE}` }));

    expect(mutate).toHaveBeenCalledWith("DROPPED");
  });

  /** A drop is the outcome most often reached by mistake, and one more press is its undo. */
  it("returns a dropped row to open", async () => {
    const user = userEvent.setup({ delay: null });
    renderRow(
      anActionItem({
        title: TITLE,
        outcome: "DROPPED",
        canClose: false,
        canReopen: true,
      }),
    );

    await user.click(
      screen.getByRole("button", { name: `Won’t do: ${TITLE}` }),
    );

    expect(mutate).toHaveBeenCalledWith("OPEN");
  });

  /** The control's whole promise: one row, one button, and each press moves it to the next
   *  outcome. Each press is asserted against the row as the server would hand it back, since the
   *  row is re-rendered from the cache rather than holding the outcome itself. */
  it("walks one row through done, dropped and back to open", async () => {
    const user = userEvent.setup({ delay: null });
    const open = anActionItem({ title: TITLE, outcome: "OPEN" });
    const { rerender } = render(
      <MemoryRouter>
        <ul>
          <ActionPointRow slug="backend-guild" item={open} users={{}} />
        </ul>
      </MemoryRouter>,
    );

    await user.click(
      screen.getByRole("button", { name: `Not done: ${TITLE}` }),
    );

    function showing(item: ActionItem) {
      rerender(
        <MemoryRouter>
          <ul>
            <ActionPointRow slug="backend-guild" item={item} users={{}} />
          </ul>
        </MemoryRouter>,
      );
    }

    showing({ ...open, outcome: "DONE", canClose: false, canReopen: true });
    await user.click(screen.getByRole("button", { name: `Done: ${TITLE}` }));

    showing({ ...open, outcome: "DROPPED", canClose: false, canReopen: true });
    await user.click(
      screen.getByRole("button", { name: `Won’t do: ${TITLE}` }),
    );

    expect(mutate.mock.calls.flat()).toEqual(["DONE", "DROPPED", "OPEN"]);
  });

  it("gives a row nobody may settle no control at all", () => {
    renderRow(
      anActionItem({ title: TITLE, canClose: false, canReopen: false }),
    );

    expect(
      screen.queryByRole("button", { name: /Not done/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Not done")).toBeInTheDocument();
  });
});
