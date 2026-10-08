import { act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { aSession, signedIn, unavailable } from "@/test/session-probe";

const renewOnce = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: vi.fn(),
  endSession: vi.fn(),
}));

async function advance(ms: number): Promise<void> {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/**
 * Signed in with a one second token, whose renewal at five seconds finds the provider down: from
 * then on every retry is one made after the held token expired.
 */
async function expiredDuringOutage(): Promise<void> {
  renewOnce.mockResolvedValue(aSession(1));
  await signedIn();
  renewOnce.mockResolvedValue(unavailable());
  await advance(5_000);
  expect(renewOnce).toHaveBeenCalledTimes(2);
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  // The middle of the spread, so a test reads the wait itself.
  vi.spyOn(Math, "random").mockReturnValue(0.5);
});

afterEach(() => {
  vi.clearAllMocks();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe("AuthProvider, retrying after the held token expired", () => {
  it("doubles the wait with each failure", async () => {
    // given
    await expiredDuringOutage();

    // then - thirty seconds, then sixty
    await advance(29_000);
    expect(renewOnce).toHaveBeenCalledTimes(2);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
    await advance(59_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(4);
  });

  it("never waits longer than five minutes", async () => {
    // given - 30, 60, 120, 240 and then the ceiling
    await expiredDuringOutage();
    await advance(30_000 + 60_000 + 120_000 + 240_000);
    expect(renewOnce).toHaveBeenCalledTimes(6);

    // then
    await advance(299_000);
    expect(renewOnce).toHaveBeenCalledTimes(6);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(7);
  });

  it("spreads the wait by up to a fifth, so tabs that failed together do not ask together", async () => {
    // given the low end of the spread
    vi.mocked(Math.random).mockReturnValue(0);

    // when
    await expiredDuringOutage();

    // then - thirty seconds less a fifth
    await advance(23_000);
    expect(renewOnce).toHaveBeenCalledTimes(2);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("waits as long as the edge asked, when that is longer", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(1));
    await signedIn();
    renewOnce.mockResolvedValue(unavailable(120));

    // when
    await advance(5_000);

    // then
    await advance(119_000);
    expect(renewOnce).toHaveBeenCalledTimes(2);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("asks again within five minutes however long the edge asked to wait", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(1));
    await signedIn();
    renewOnce.mockResolvedValue(unavailable(3_600));

    // when
    await advance(5_000);

    // then
    await advance(300_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("waits as long as the edge asked even while the held token still works", async () => {
    // given - a five minute token, renewed at four and a half minutes into an outage
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    renewOnce.mockResolvedValue(unavailable(60));
    await advance(270_000);
    expect(renewOnce).toHaveBeenCalledTimes(2);

    // then - not fifteen seconds later, but the minute asked for
    await advance(15_000);
    expect(renewOnce).toHaveBeenCalledTimes(2);
    await advance(45_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it.each([
    ["the tab is looked at again", document, "visibilitychange"],
    ["the device is back online", window, "online"],
  ] as const)(
    "asks at once and starts the back-off over when %s",
    async (_, target, event) => {
      // given - two failures, so the next wait would be a minute
      await expiredDuringOutage();
      await advance(30_000);
      expect(renewOnce).toHaveBeenCalledTimes(3);

      // when
      act(() => {
        target.dispatchEvent(new Event(event));
      });
      await advance(0);

      // then - thirty seconds again
      expect(renewOnce).toHaveBeenCalledTimes(4);
      await advance(30_000);
      expect(renewOnce).toHaveBeenCalledTimes(5);
    },
  );

  it("starts the back-off over after a renewal succeeds", async () => {
    // given - two failures, then a one second token
    await expiredDuringOutage();
    await advance(30_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
    renewOnce.mockResolvedValue(aSession(1));
    await advance(60_000);
    expect(renewOnce).toHaveBeenCalledTimes(4);

    // when - its renewal finds the provider down again
    renewOnce.mockResolvedValue(unavailable());
    await advance(5_000);
    expect(renewOnce).toHaveBeenCalledTimes(5);

    // then - thirty seconds, not the two minutes a third failure in a row would wait
    await advance(30_000);
    expect(renewOnce).toHaveBeenCalledTimes(6);
  });

  it("goes back to the token's own schedule once a renewal succeeds", async () => {
    // given
    await expiredDuringOutage();
    renewOnce.mockResolvedValue(aSession());
    await advance(30_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);

    // then - a 300 second token renews at 270 s, not on the back-off
    await advance(269_000);
    expect(renewOnce).toHaveBeenCalledTimes(3);
    await advance(1_000);
    expect(renewOnce).toHaveBeenCalledTimes(4);
  });
});
