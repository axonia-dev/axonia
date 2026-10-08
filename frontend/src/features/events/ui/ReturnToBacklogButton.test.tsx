import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useReturnToBacklog } from "@/features/events/api/use-backlog-pull";
import { ReturnToBacklogButton } from "@/features/events/ui/ReturnToBacklogButton";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/events/api/use-backlog-pull", () => ({
  useReturnToBacklog: vi.fn(),
}));

const mockUseReturnToBacklog = vi.mocked(useReturnToBacklog);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseReturnToBacklog.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("ReturnToBacklogButton", () => {
  it("returns the topic to the backlog on press", async () => {
    // given
    const mutate = vi.fn();
    mockUseReturnToBacklog.mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    render(<ReturnToBacklogButton slug="backend-guild" topicId={42} />);

    // when
    await user.click(screen.getByRole("button", { name: /move to backlog/i }));

    // then
    expect(mutate).toHaveBeenCalledWith(42);
  });

  it("shows an error message when the return fails", () => {
    // given
    mockUseReturnToBacklog.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: new ApiError(409, "x", "NOT_SCHEDULED"),
    } as never);

    // when
    render(<ReturnToBacklogButton slug="backend-guild" topicId={42} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(/not scheduled/i);
  });
});
