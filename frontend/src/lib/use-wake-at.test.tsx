import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useNow, useWakeAt } from "@/lib/use-wake-at";

const T0 = new Date("2026-08-18T15:00:00Z").getTime();

/** Renders the instant it last saw, so a re-render is observable rather than inferred. */
function Clock({ wakeAt }: Readonly<{ wakeAt: number | null }>) {
  useWakeAt(wakeAt);
  return <p>{useNow().toISOString()}</p>;
}

function shown(): string {
  return screen.getByText(/^2026-/).textContent ?? "";
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(T0);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useWakeAt", () => {
  it("re-renders the caller once the clock reaches the instant it registered", async () => {
    render(<Clock wakeAt={T0 + 60_000} />);
    expect(shown()).toBe("2026-08-18T15:00:00.000Z");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(shown()).toBe("2026-08-18T15:01:00.000Z");
  });

  it("leaves the caller alone until then", async () => {
    render(<Clock wakeAt={T0 + 60_000} />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(59_000);
    });

    expect(shown()).toBe("2026-08-18T15:00:00.000Z");
  });

  it("waits on nothing for a caller with no instant to wait for", () => {
    render(<Clock wakeAt={null} />);

    expect(vi.getTimerCount()).toBe(0);
  });

  it("gives up its wait when the caller unmounts", () => {
    const { unmount } = render(<Clock wakeAt={T0 + 60_000} />);
    expect(vi.getTimerCount()).toBe(1);

    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });

  it("keeps waiting for the callers that remain", () => {
    const { unmount } = render(<Clock wakeAt={T0 + 60_000} />);
    render(<Clock wakeAt={T0 + 120_000} />);
    expect(vi.getTimerCount()).toBe(1);

    unmount();

    expect(vi.getTimerCount()).toBe(1);
  });

  it("waits on one timer however many callers register", () => {
    render(
      <>
        <Clock wakeAt={T0 + 60_000} />
        <Clock wakeAt={T0 + 120_000} />
        <Clock wakeAt={T0 + 90_000} />
      </>,
    );

    expect(vi.getTimerCount()).toBe(1);
  });

  it("wakes every caller at the earliest instant any of them asked for", async () => {
    render(
      <>
        <Clock wakeAt={T0 + 60_000} />
        <Clock wakeAt={T0 + 600_000} />
      </>,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    const seen = screen.getAllByText(/^2026-/).map((node) => node.textContent);
    expect(seen).toEqual([
      "2026-08-18T15:01:00.000Z",
      "2026-08-18T15:01:00.000Z",
    ]);
  });

  it("re-arms for a caller still waiting after an earlier one has been served", async () => {
    render(
      <>
        <Clock wakeAt={T0 + 60_000} />
        <Clock wakeAt={T0 + 600_000} />
      </>,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(vi.getTimerCount()).toBe(1);
  });
});
