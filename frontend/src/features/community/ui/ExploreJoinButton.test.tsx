import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
let onJoined: (() => void | Promise<void>) | undefined;
const mutate = vi.fn();

function mockJoin(state: Record<string, unknown> = {}) {
  mockUseJoin.mockImplementation((_slug, callback) => {
    onJoined = callback;
    return { mutate, isPending: false, isError: false, ...state } as never;
  });
}

function CommunityHome() {
  const dark = document.querySelector("[data-ceremony-curtain]") !== null;
  return <p>{dark ? "home under the curtain" : "home in plain sight"}</p>;
}

function renderButton() {
  return render(
    <MemoryRouter initialEntries={["/communities"]}>
      <Routes>
        <Route
          path="/communities"
          element={<ExploreJoinButton slug="backend-guild" />}
        />
        <Route path="/communities/:slug" element={<CommunityHome />} />
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

afterEach(() => {
  document.querySelector("[data-ceremony-curtain]")?.remove();
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

  it("hands off the welcome and enters the community under the curtain once the join succeeds", async () => {
    // given
    renderButton();

    // when
    await act(async () => {
      await onJoined?.();
    });

    // then
    expect(handOffCeremony).toHaveBeenCalledWith("welcome", "backend-guild");
    expect(
      await screen.findByText("home under the curtain"),
    ).toBeInTheDocument();
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

  it("cannot be pressed twice while joining", async () => {
    // given
    mockJoin({ isPending: true });
    renderButton();

    // when
    await user.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(mutate).not.toHaveBeenCalled();
  });

  it("shows a spinner and keeps the label while joining", () => {
    // given
    mockJoin({ isPending: true });

    // when
    renderButton();

    // then
    const button = screen.getByRole("button", { name: "Join" });
    expect(button.querySelector("[data-button-spinner]")).not.toBeNull();
  });

  it("shows no spinner when idle", () => {
    // when
    renderButton();

    // then
    expect(
      screen
        .getByRole("button", { name: "Join" })
        .querySelector("[data-button-spinner]"),
    ).toBeNull();
  });
});
