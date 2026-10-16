import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useLocalClock } from "@/lib/use-local-clock";

const T0 = new Date("2026-08-18T15:00:00Z").getTime();
const MINUTE_MS = 60_000;

function Clock({ active }: Readonly<{ active: boolean }>) {
  return <p>{useLocalClock(1000, active).toISOString()}</p>;
}

const shown = () => screen.getByText(/^2026-/).textContent;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(T0);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useLocalClock", () => {
  it("shows the current time at once when it becomes active again", async () => {
    // given
    const { rerender } = render(<Clock active />);
    rerender(<Clock active={false} />);
    act(() => {
      vi.setSystemTime(T0 + 5 * MINUTE_MS);
    });
    expect(shown()).toBe("2026-08-18T15:00:00.000Z");

    // when
    rerender(<Clock active />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });

    // then
    expect(shown()).toBe("2026-08-18T15:05:00.000Z");
  });
});
