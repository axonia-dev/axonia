import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { useJoin } from "@/features/community/api/use-membership";
import { ExploreJoinButton } from "@/features/community/ui/ExploreJoinButton";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));
vi.mock("@/features/community/api/ceremony-handoff", () => ({
  handOffCeremony: vi.fn(),
}));

const mockUseJoin = vi.mocked(useJoin);

let user: ReturnType<typeof userEvent.setup>;
let onJoined: (() => void) | undefined;
const mutate = vi.fn();

function mockJoin(state: Record<string, unknown> = {}) {
  mockUseJoin.mockImplementation((_slug, callback) => {
    onJoined = callback;
    return { mutate, isPending: false, isError: false, ...state } as never;
  });
}

function renderButton() {
  return render(
    <MemoryRouter initialEntries={["/communities"]}>
      <Routes>
        <Route
          path="/communities"
          element={<ExploreJoinButton slug="backend-guild" />}
        />
        <Route path="/communities/:slug" element={<p>community home</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  onJoined = undefined;
  mockJoin();
});

describe("ExploreJoinButton", () => {
  it("joins when pressed", async () => {
    // given
    renderButton();

    // when
    await user.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("hands off the welcome and enters the community once the join succeeds", () => {
    // given
    renderButton();

    // when
    act(() => onJoined?.());

    // then
    expect(handOffCeremony).toHaveBeenCalledWith("welcome", "backend-guild");
    expect(screen.getByText("community home")).toBeInTheDocument();
  });

  it("stays on the page and says why when the join fails", () => {
    // given
    mockJoin({
      isError: true,
      error: new ApiError(409, "x", "ALREADY_MEMBER"),
    });

    // when
    renderButton();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      /already a member of this community/i,
    );
    expect(handOffCeremony).not.toHaveBeenCalled();
  });

  it("cannot be pressed twice while joining", () => {
    // given
    mockJoin({ isPending: true });

    // when
    renderButton();

    // then
    expect(screen.getByRole("button", { name: "Join" })).toBeDisabled();
  });
});
