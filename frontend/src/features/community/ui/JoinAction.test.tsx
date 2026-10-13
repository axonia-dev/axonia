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
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  } as never);
});

describe("JoinAction", () => {
  it("fires join", async () => {
    // given
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    mockUseJoin.mockReturnValue({ mutateAsync, isPending: false } as never);
    render(<JoinAction slug="backend-guild" />);

    // when
    await user.click(screen.getByRole("button", { name: /join/i }));

    // then
    expect(mutateAsync).toHaveBeenCalledOnce();
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

  it("reports a join once it has succeeded", async () => {
    // given
    const onJoined = vi.fn();
    render(<JoinAction slug="backend-guild" onJoined={onJoined} />);

    // when
    await user.click(screen.getByRole("button", { name: /join/i }));

    // then
    expect(onJoined).toHaveBeenCalledOnce();
  });

  it("reports a join that succeeds after the button has gone", async () => {
    // given
    let succeed: () => void = () => {};
    mockUseJoin.mockReturnValue({
      mutateAsync: vi.fn(
        () =>
          new Promise<void>((resolve) => {
            succeed = resolve;
          }),
      ),
      isPending: false,
    } as never);
    const onJoined = vi.fn();
    const { unmount } = render(
      <JoinAction slug="backend-guild" onJoined={onJoined} />,
    );
    await user.click(screen.getByRole("button", { name: /join/i }));
    unmount();

    // when
    succeed();
    await Promise.resolve();

    // then
    expect(onJoined).toHaveBeenCalledOnce();
  });
});
