import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import {
  afterAll,
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

import {
  aSession,
  renderSessionProbe,
  signedIn,
  TEST_NAMES,
  unavailable,
} from "@/test/session-probe";

const renewOnce = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: vi.fn(),
  endSession: vi.fn(),
}));

const CHANNEL = TEST_NAMES.channel;

function pendingRenewal() {
  let answer: (value: unknown) => void = () => undefined;
  const renewal = new Promise<unknown>((resolve) => {
    answer = resolve;
  });
  return { renewal, answer };
}

const sibling = new BroadcastChannel(CHANNEL);

afterAll(() => {
  sibling.close();
});

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.unstubAllGlobals();
  sibling.onmessage = null;
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("AuthProvider, renewing a token that has already expired", () => {
  it("says a renewal is under way until it answers", async () => {
    // given a token that expired while no timer ran, as in a sleeping laptop
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    vi.setSystemTime(Date.now() + 10 * 60_000);
    const { renewal, answer } = pendingRenewal();
    renewOnce.mockReturnValueOnce(renewal);

    // when
    fireEvent.click(screen.getByRole("button", { name: "renew" }));

    // then
    await waitFor(() =>
      expect(screen.getByTestId("pending")).toHaveTextContent("true"),
    );
    await act(async () => {
      answer(unavailable());
      await renewal;
    });
    expect(screen.getByTestId("pending")).toHaveTextContent("false");
    expect(screen.getByTestId("unavailable")).toHaveTextContent("true");
  });

  it("still says so while a second renewal for it is under way", async () => {
    // given the tab came back while its due renewal was already asking
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    vi.setSystemTime(Date.now() + 10 * 60_000);
    const first = pendingRenewal();
    const second = pendingRenewal();
    renewOnce
      .mockReturnValueOnce(first.renewal)
      .mockReturnValueOnce(second.renewal);
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() => expect(renewOnce).toHaveBeenCalledTimes(3));

    // when
    await act(async () => {
      first.answer(unavailable());
      await first.renewal;
    });

    // then
    expect(screen.getByTestId("pending")).toHaveTextContent("true");
    await act(async () => {
      second.answer(unavailable());
      await second.renewal;
    });
    expect(screen.getByTestId("pending")).toHaveTextContent("false");
  });

  it("says nothing of a renewal for a token that still works", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    renewOnce.mockReturnValueOnce(pendingRenewal().renewal);

    // when
    fireEvent.click(screen.getByRole("button", { name: "renew" }));

    // then
    await expect(
      waitFor(
        () => expect(screen.getByTestId("pending")).toHaveTextContent("true"),
        { timeout: 200 },
      ),
    ).rejects.toThrow();
  });
});

describe("AuthProvider, beside other tabs", () => {
  it("tells the other tabs when it renewed", async () => {
    // given
    const heard = vi.fn();
    sibling.onmessage = heard;
    renewOnce.mockResolvedValue(aSession(300));

    // when
    await signedIn();

    // then
    await waitFor(() => expect(heard).toHaveBeenCalledOnce());
  });

  it("asks again at once when another tab renewed while it waits out an outage", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    renewOnce.mockResolvedValue(unavailable());
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("true"),
    );
    renewOnce.mockResolvedValue(aSession(300));

    // when
    sibling.postMessage("renewed");

    // then
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("false"),
    );
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("asks once per waiting tab when another tab renewed, however the renewals queue", async () => {
    // given three tabs waiting out an outage
    renewOnce.mockResolvedValue(aSession(300));
    for (let tab = 0; tab < 3; tab += 1) renderSessionProbe();
    await waitFor(() =>
      expect(
        screen.getAllByTestId("state").map((state) => state.textContent),
      ).toEqual(["in", "in", "in"]),
    );
    renewOnce.mockResolvedValue(unavailable());
    for (const renew of screen.getAllByRole("button", { name: "renew" })) {
      fireEvent.click(renew);
    }
    await waitFor(() =>
      expect(
        screen.getAllByTestId("unavailable").map((tab) => tab.textContent),
      ).toEqual(["true", "true", "true"]),
    );
    renewOnce.mockClear();
    // the browser lets one renewal run at a time, so the tabs behind hear the first answer
    const queued: Array<() => void> = [];
    renewOnce.mockImplementation(
      () =>
        new Promise((resolve) => {
          queued.push(() => resolve(aSession(300)));
        }),
    );

    // when
    sibling.postMessage("renewed");
    await waitFor(() => expect(renewOnce).toHaveBeenCalledTimes(3));
    while (queued.length > 0) {
      await act(async () => {
        queued.shift()?.();
        await new Promise((settled) => setTimeout(settled, 50));
      });
    }

    // then
    expect(
      screen.getAllByTestId("unavailable").map((tab) => tab.textContent),
    ).toEqual(["false", "false", "false"]);
    expect(renewOnce).toHaveBeenCalledTimes(3);
  });

  it("still signs in where no tab can hear another", async () => {
    // given
    vi.stubGlobal("BroadcastChannel", undefined);
    renewOnce.mockResolvedValue(aSession(300));

    // when
    await signedIn();

    // then
    expect(screen.getByTestId("state")).toHaveTextContent("in");
  });

  it("leaves its own schedule alone once its own renewal ended the outage", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();
    renewOnce.mockResolvedValueOnce(unavailable());
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("true"),
    );
    fireEvent.click(screen.getByRole("button", { name: "renew" }));
    await waitFor(() =>
      expect(screen.getByTestId("unavailable")).toHaveTextContent("false"),
    );

    // when
    sibling.postMessage("renewed");

    // then
    await expect(
      waitFor(() => expect(renewOnce).toHaveBeenCalledTimes(4), {
        timeout: 200,
      }),
    ).rejects.toThrow();
  });

  it("leaves its own schedule alone when another tab renewed and its own renewals work", async () => {
    // given
    renewOnce.mockResolvedValue(aSession(300));
    await signedIn();

    // when
    sibling.postMessage("renewed");

    // then
    await expect(
      waitFor(() => expect(renewOnce).toHaveBeenCalledTimes(2), {
        timeout: 200,
      }),
    ).rejects.toThrow();
  });
});
