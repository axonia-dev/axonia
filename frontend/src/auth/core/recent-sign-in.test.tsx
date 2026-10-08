import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { renderSessionProbe, TEST_NAMES } from "@/test/session-probe";

import { markSignInStarted, signInStartedRecently } from "./recent-sign-in";

const renewOnce = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: vi.fn(),
  endSession: vi.fn(),
}));

const KEY = "an-app.sign-in-started";

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  sessionStorage.clear();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("a sign-in this tab started", () => {
  it("is recent for a minute after it started", () => {
    // given
    markSignInStarted(KEY);

    // when
    vi.advanceTimersByTime(59_999);

    // then
    expect(signInStartedRecently(KEY)).toBe(true);
  });

  it("is no longer recent once a minute has passed", () => {
    // given
    markSignInStarted(KEY);

    // when
    vi.advanceTimersByTime(60_000);

    // then
    expect(signInStartedRecently(KEY)).toBe(false);
  });

  it("is not recent when this tab never left for one", () => {
    // when
    const actual = signInStartedRecently(KEY);

    // then
    expect(actual).toBe(false);
  });

  it("is not recent when it was remembered as starting later than now", () => {
    // given - a clock set back since
    sessionStorage.setItem(KEY, String(Date.now() + 5_000));

    // when
    const actual = signInStartedRecently(KEY);

    // then
    expect(actual).toBe(false);
  });

  it("is not recent when what was remembered is not a time", () => {
    // given
    sessionStorage.setItem(KEY, "soon");

    // when
    const actual = signInStartedRecently(KEY);

    // then
    expect(actual).toBe(false);
  });

  it.each([
    ["remembered", "setItem"],
    ["read", "getItem"],
  ] as const)(
    "costs only the guard when sessionStorage refuses to be %s",
    (_, method) => {
      // given
      vi.spyOn(Storage.prototype, method).mockImplementation(() => {
        throw new DOMException("blocked", "SecurityError");
      });

      // when
      markSignInStarted(KEY);

      // then
      expect(signInStartedRecently(KEY)).toBe(false);
      vi.restoreAllMocks();
    },
  );
});

describe("AuthProvider, leaving for a sign-in", () => {
  const asked = (): string => {
    fireEvent.click(screen.getByRole("button", { name: "recent" }));
    return screen.getByTestId("recent").textContent ?? "";
  };

  it("remembers in this tab, under the app's own key, that it left", async () => {
    // given
    renewOnce.mockResolvedValue(null);
    renderSessionProbe();
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("out"),
    );
    expect(asked()).toBe("false");

    // when
    act(() => {
      screen.getByRole("button", { name: "sign in" }).click();
    });

    // then
    expect(asked()).toBe("true");
    expect(sessionStorage.getItem(TEST_NAMES.signInStarted)).not.toBeNull();
  });
});
