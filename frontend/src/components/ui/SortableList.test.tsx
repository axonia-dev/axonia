import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { reorderByIds, SortableList } from "@/components/ui/SortableList";
import { stubVerticalLayout } from "@/test/dndkit";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("reorderByIds", () => {
  it("moves the active id onto the over id", () => {
    expect(reorderByIds(["a", "b", "c"], "a", "c")).toEqual(["b", "c", "a"]);
  });

  it("is a no-op when active and over are the same", () => {
    expect(reorderByIds(["a", "b"], "a", "a")).toBeNull();
  });

  it("is a no-op when an id is unknown", () => {
    expect(reorderByIds(["a", "b"], "a", "z")).toBeNull();
  });
});

describe("SortableList", () => {
  beforeEach(stubVerticalLayout);
  afterEach(() => vi.restoreAllMocks());

  it("reorders via the keyboard sensor (Space, ArrowDown, Space)", async () => {
    const onReorder = vi.fn();
    render(
      <SortableList ids={["a", "b"]} onReorder={onReorder}>
        {(id, handle) => (
          <div>
            <button {...handle} aria-label={`drag ${id}`}>
              ⠿
            </button>
            <span>{id}</span>
          </div>
        )}
      </SortableList>,
    );
    const handle = screen.getByRole("button", { name: "drag a" });
    handle.focus();
    await user.keyboard("{ }"); // pick up
    await user.keyboard("{ArrowDown}");
    await user.keyboard("{ }"); // drop
    expect(onReorder).toHaveBeenCalledWith(["b", "a"]);
  });
});
