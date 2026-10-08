import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BatchMergeBar } from "@/features/expertise/ui/BatchMergeBar";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function setup(over: Partial<Parameters<typeof BatchMergeBar>[0]> = {}) {
  const props = {
    selected: ["yep", "ok", "yes"],
    survivor: "yes",
    onSurvivorChange: vi.fn(),
    onMerge: vi.fn(),
    onClear: vi.fn(),
    ...over,
  };
  render(<BatchMergeBar {...props} />);
  return props;
}

describe("BatchMergeBar", () => {
  it("shows the count of variants being merged (selected minus survivor)", () => {
    setup();
    expect(
      screen.getByRole("button", { name: /merge 2/i }),
    ).toBeInTheDocument();
  });

  it("merges and clears via the buttons", async () => {
    const props = setup();
    await user.click(screen.getByRole("button", { name: /merge 2/i }));
    expect(props.onMerge).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: /clear/i }));
    expect(props.onClear).toHaveBeenCalledTimes(1);
  });

  it("drops the count and offers a swap when exactly two are selected", async () => {
    const props = setup({
      selected: ["infrastructure", "confluent"],
      survivor: "confluent",
    });
    // Singular label, no stray "1".
    expect(
      screen.getByRole("button", { name: /^merge$/i }),
    ).toBeInTheDocument();
    // The non-survivor is shown merging into the survivor.
    expect(screen.getByText("infrastructure")).toBeInTheDocument();
    expect(screen.getByText("confluent")).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /swap which tag survives/i }),
    );
    // Survivor flips to the other selected tag.
    expect(props.onSurvivorChange).toHaveBeenCalledWith("infrastructure");
  });
});
