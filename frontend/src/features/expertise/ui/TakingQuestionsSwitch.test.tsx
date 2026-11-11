import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSetPaused } from "@/features/expertise/api/use-expertise-prefs";

import { TakingQuestionsSwitch } from "./TakingQuestionsSwitch";

vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useSetPaused: vi.fn(),
}));

let mutate: ReturnType<typeof vi.fn>;

beforeEach(() => {
  mutate = vi.fn();
  vi.mocked(useSetPaused).mockReturnValue({
    mutate,
    isPending: false,
    variables: undefined,
  } as never);
});

describe("TakingQuestionsSwitch", () => {
  it("turningOffCallsSetPausedTrue", async () => {
    // given
    render(<TakingQuestionsSwitch paused={false} inCommunity={false} />);
    const toggle = screen.getByRole("switch", { name: "Taking questions" });
    expect(toggle).toBeChecked();

    // when
    await userEvent.click(toggle);

    // then
    expect(mutate).toHaveBeenCalledWith(true);
  });

  it("turningBackOnCallsSetPausedFalse", async () => {
    // given
    render(<TakingQuestionsSwitch paused inCommunity={false} />);

    // when
    await userEvent.click(
      screen.getByRole("switch", { name: "Taking questions" }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith(false);
  });

  it("explainsThatPausingLosesNothing", () => {
    // when
    render(<TakingQuestionsSwitch paused={false} inCommunity={false} />);

    // then
    expect(
      screen.getByText("Paused, the ring disappears and nothing is lost"),
    ).toBeInTheDocument();
  });

  it("showsTheChoiceInFlightBeforeTheServerAnswers", () => {
    // given
    vi.mocked(useSetPaused).mockReturnValue({
      mutate,
      isPending: true,
      variables: true,
    } as never);

    // when
    render(<TakingQuestionsSwitch paused={false} inCommunity={false} />);

    // then
    expect(
      screen.getByRole("switch", { name: "Taking questions" }),
    ).not.toBeChecked();
  });

  it("saysItAppliesInEveryCommunityOnACommunityPage", () => {
    // when
    render(<TakingQuestionsSwitch paused={false} inCommunity />);

    // then
    expect(screen.getByText("Applies in every community")).toHaveClass(
      "text-muted-foreground",
    );
  });

  it("saysNothingMoreAcrossEveryCommunity", () => {
    // when
    render(<TakingQuestionsSwitch paused={false} inCommunity={false} />);

    // then
    expect(
      screen.queryByText("Applies in every community"),
    ).not.toBeInTheDocument();
  });
});
