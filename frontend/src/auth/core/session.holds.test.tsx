import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { aSession, signedIn, unavailable } from "@/test/session-probe";

const renewOnce = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: vi.fn(),
  endSession: vi.fn(),
}));

function pendingRenewal() {
  let answer: (value: unknown) => void = () => undefined;
  const renewal = new Promise<unknown>((resolve) => {
    answer = resolve;
  });
  return { renewal, answer };
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

// These ask from a click, after act has flushed the render, so on their own they cannot tell the
// session's answer from rendered state. The real-provider probes pin that it answers before React
// renders: AuthGuard.returning-tab.test.tsx among them.
describe("AuthProvider, asked whether a 401 is held", () => {
  const asked = (): string => {
    fireEvent.click(screen.getByRole("button", { name: "holds" }));
    return screen.getByTestId("holds").textContent ?? "";
  };

  it("holds while the provider cannot be reached, and no longer once a renewal succeeds", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    expect(asked()).toBe("false");
    renewOnce.mockResolvedValueOnce(unavailable());

    // when
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("true"),
    );

    // then
    expect(asked()).toBe("true");
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("false"),
    );
    expect(asked()).toBe("false");
  });

  it("holds while a renewal for an expired token is under way", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    vi.setSystemTime(Date.now() + 10 * 60_000);
    const { renewal, answer } = pendingRenewal();
    renewOnce.mockReturnValueOnce(renewal);

    // when
    fireEvent.click(screen.getByRole("button", { name: "renew" }));

    // then
    expect(asked()).toBe("true");
    await act(async () => {
      answer(aSession(300));
      await renewal;
    });
    expect(asked()).toBe("false");
  });
});
