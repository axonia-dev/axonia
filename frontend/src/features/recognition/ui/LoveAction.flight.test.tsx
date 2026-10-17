import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useLove,
  useLoveSummary,
  useUnlove,
} from "@/features/recognition/api/use-recognition";
import { launchLoveFlight } from "@/features/recognition/ui/love-flight";
import { LoveAction } from "@/features/recognition/ui/LoveAction";
import { useMediaQuery } from "@/lib/use-media-query";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useLoveSummary: vi.fn(),
  useLove: vi.fn(),
  useUnlove: vi.fn(),
}));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "dev-user" }));
vi.mock("@/features/recognition/ui/love-flight", () => ({
  launchLoveFlight: vi.fn(),
}));
vi.mock("@/lib/use-media-query", () => ({ useMediaQuery: vi.fn() }));

const ref = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 9,
};

const BURST_SPANS = "span[style*=burst-fly]";

let mutate: ReturnType<typeof vi.fn>;
let unlove: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  mutate = vi.fn();
  unlove = vi.fn();
  vi.mocked(useMediaQuery).mockReturnValue(false);
  vi.mocked(useLove).mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUnlove).mockReturnValue({
    mutate: unlove,
    isPending: false,
  } as never);
  vi.mocked(useLoveSummary).mockReturnValue({
    data: { count: 2, viewerHasLoved: false },
  } as never);
});

const burstCount = (container: HTMLElement) =>
  [...container.querySelectorAll(BURST_SPANS)].filter(
    (el) => el.textContent === "♥",
  ).length;

describe("LoveAction flight", () => {
  it("flies the comet and skips the burst when a target is credited", async () => {
    // given
    vi.mocked(launchLoveFlight).mockReturnValue(true);
    const { container } = render(<LoveAction contribution={ref} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: /^love/i }));

    // then
    expect(launchLoveFlight).toHaveBeenCalledWith(
      expect.objectContaining({ contribution: ref, viewerId: "dev-user" }),
    );
    expect(burstCount(container)).toBe(0);
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("falls back to the eight-heart burst when nothing is launched", async () => {
    // given
    vi.mocked(launchLoveFlight).mockReturnValue(false);
    const { container } = render(<LoveAction contribution={ref} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: /^love/i }));

    // then
    expect(burstCount(container)).toBe(8);
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("shows no motion under reduced motion but still loves", async () => {
    // given
    vi.mocked(useMediaQuery).mockReturnValue(true);
    const { container } = render(<LoveAction contribution={ref} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: /^love/i }));

    // then
    expect(launchLoveFlight).not.toHaveBeenCalled();
    expect(burstCount(container)).toBe(0);
    expect(mutate).toHaveBeenCalledTimes(1);
  });

  it("never launches a flight on unlove", async () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 2, viewerHasLoved: true },
    } as never);
    render(<LoveAction contribution={ref} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: /unlove/i }));

    // then
    expect(launchLoveFlight).not.toHaveBeenCalled();
    expect(unlove).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["the first love", 0],
    ["a further love", 2],
  ])(
    "launches %s from the heart icon, not the whole button",
    async (_, count) => {
      vi.mocked(useLoveSummary).mockReturnValue({
        data: { count, viewerHasLoved: false },
      } as never);
      vi.mocked(launchLoveFlight).mockReturnValue(true);
      render(<LoveAction contribution={ref} />);
      const button = screen.getByRole("button", { name: /^love/i });

      await userEvent.click(button);

      const origin = vi.mocked(launchLoveFlight).mock.calls[0]?.[0].origin;
      expect(origin?.parentElement).toBe(button);
      expect(origin?.children).toHaveLength(1);
      expect(origin?.firstElementChild?.tagName.toLowerCase()).toBe("svg");
    },
  );
});
