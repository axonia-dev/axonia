import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TypeChips } from "@/features/knowledge/ui/TypeChips";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(),
}));

import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";

const mockTypes = vi.mocked(useArtifactTypes);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockTypes.mockReturnValue({
    data: [
      { id: 3, label: "Recording", icon: null, accentColor: null },
      { id: 4, label: "RFC", icon: null, accentColor: null },
    ],
  } as never);
});

describe("TypeChips", () => {
  it("renders an All chip plus one chip per type", () => {
    // when
    render(<TypeChips selected={0} onSelect={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: "All" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Recording/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /RFC/ })).toBeInTheDocument();
  });

  it("selects a type when its chip is clicked", async () => {
    // given
    const onSelect = vi.fn();
    render(<TypeChips selected={0} onSelect={onSelect} />);

    // when
    await user.click(screen.getByRole("button", { name: /Recording/ }));

    // then
    expect(onSelect).toHaveBeenCalledWith(3);
  });

  it("clears the filter when the active chip is clicked again", async () => {
    // given
    const onSelect = vi.fn();
    render(<TypeChips selected={3} onSelect={onSelect} />);

    // when
    await user.click(screen.getByRole("button", { name: /Recording/ }));

    // then
    expect(onSelect).toHaveBeenCalledWith(0);
  });
});
