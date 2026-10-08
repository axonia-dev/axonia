import { act, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  aSession,
  renderSessionProbe,
  signedIn,
  unavailable,
} from "@/test/session-probe";

const renewOnce = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: vi.fn(),
  endSession: vi.fn(),
}));

beforeEach(() => {
  renewOnce.mockResolvedValue(aSession());
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("AuthProvider, when the identity provider cannot be reached", () => {
  it("keeps the person and the token when a renewal finds the provider unreachable", async () => {
    // given
    await signedIn();
    renewOnce.mockResolvedValue(unavailable());

    // when
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("true"),
    );
    expect(screen.getByTestId("state")).toHaveTextContent("in");
    expect(screen.getByTestId("sub")).toHaveTextContent("alice-sub");
  });

  it("answers a silent renewal the provider could not serve with the person still held", async () => {
    // given
    await signedIn();
    renewOnce.mockResolvedValue(unavailable());

    // when
    act(() => {
      screen.getByRole("button", { name: "renew" }).click();
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("silent")).toHaveTextContent("alice-sub"),
    );
  });

  it("holds nobody once the token has been dropped, even while the provider is unreachable", async () => {
    // given a token the backend refused, dropped by the 401 handler
    await signedIn();
    act(() => {
      screen.getByRole("button", { name: "drop" }).click();
    });
    renewOnce.mockResolvedValue(unavailable());

    // when
    act(() => {
      screen.getByRole("button", { name: "renew" }).click();
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("silent")).toHaveTextContent("none"),
    );
  });

  it("tries again on a timer rather than in a loop while the held token still works", async () => {
    // given - the scheduled renewal of a 300 second token finds the provider down
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await signedIn();
    renewOnce.mockResolvedValue(unavailable());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(270_000);
    });
    expect(renewOnce).toHaveBeenCalledTimes(2);

    // when - fifteen seconds is the retry, so fourteen is still too early
    await act(async () => {
      await vi.advanceTimersByTimeAsync(14_000);
    });
    expect(renewOnce).toHaveBeenCalledTimes(2);
    renewOnce.mockResolvedValue(aSession());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });

    // then - the provider is back, and the person never left
    expect(renewOnce).toHaveBeenCalledTimes(3);
    expect(screen.getByTestId("state")).toHaveTextContent("in");
    expect(screen.getByTestId("unavailable")).toHaveTextContent("false");
  });

  it("replaces the scheduled renewal with the retry rather than running both", async () => {
    // given - a 300 second token, so the scheduled renewal is due at 270 s
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await signedIn();
    renewOnce.mockResolvedValueOnce(unavailable());

    // when - a tab return finds the provider down, and the retry 15 s later finds it back
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(284_000);
    });

    // then - mount, tab return, retry; the renewal first scheduled for 270 s never ran
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("settles signed out on a first visit the provider cannot answer, and says sign-in is unavailable", async () => {
    // given - nothing is held yet, so there is no token to keep
    renewOnce.mockResolvedValue(unavailable());

    // when
    renderSessionProbe();

    // then
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("out"),
    );
    expect(screen.getByTestId("unavailable")).toHaveTextContent("true");
  });

  it("stops saying sign-in is unavailable once a renewal is refused", async () => {
    // given
    await signedIn();
    renewOnce.mockResolvedValue(unavailable());
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("true"),
    );
    renewOnce.mockResolvedValue(null);

    // when
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("out"),
    );
    expect(screen.getByTestId("unavailable")).toHaveTextContent("false");
  });
});
