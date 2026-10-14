import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useJoin } from "@/features/community/api/use-membership";
import { JoinAction } from "@/features/community/ui/JoinAction";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));

const mockUseJoin = vi.mocked(useJoin);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseJoin.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("JoinAction", () => {
  it("fires join", async () => {
    // given
    const mutate = vi.fn();
    mockUseJoin.mockReturnValue({ mutate, isPending: false } as never);
    render(<JoinAction slug="backend-guild" />);

    // when
    await user.click(screen.getByRole("button", { name: /join/i }));

    // then
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("shows a human message when join fails", () => {
    // given
    mockUseJoin.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      error: new ApiError(409, "x", "ALREADY_MEMBER"),
    } as never);

    // when
    render(<JoinAction slug="backend-guild" />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      /already a member of this community/i,
    );
  });

  it("shows the same pending spinner as every Join while joining, and ignores a second press", async () => {
    // given
    const mutate = vi.fn();
    mockUseJoin.mockReturnValue({ mutate, isPending: true } as never);
    render(<JoinAction slug="backend-guild" />);
    const join = screen.getByRole("button", { name: "Join" });

    // when
    await user.click(join);

    // then
    expect(join.querySelector("[data-button-spinner]")).not.toBeNull();
    expect(mutate).not.toHaveBeenCalled();
  });
});
