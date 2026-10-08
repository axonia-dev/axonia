// A tab coming back from the background or from sleep holds a token that expired meanwhile. On
// the same event the session renews and the unread badge refetches, and the badge's 401 comes back
// long before a renewal waiting on an identity provider that does not answer.

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  type MockInstance,
  vi,
} from "vitest";

import { AuthGuard } from "@/auth/AuthGuard";
import { AuthProvider } from "@/auth/session";
import {
  getAccessToken,
  notifyUnauthorized,
  setAccessToken,
} from "@/auth/token";
import { toastQueue, ToastViewport } from "@/components/ui/toast";
import { useUnreadCount } from "@/features/communication/api/use-unread-count";
import { aSession, unavailable } from "@/test/session-probe";

const renewOnce = vi.fn();
const startLogin = vi.fn();

vi.mock("@/auth/core/session-client", () => ({
  renewOnce: () => renewOnce() as unknown,
  startLogin: (options?: unknown) => startLogin(options) as unknown,
  endSession: vi.fn(),
}));

const unreadCount = (count: number) =>
  new Response(JSON.stringify({ count }), {
    status: 200,
    headers: { "content-type": "application/json" },
  });

const refused = () =>
  new Response(JSON.stringify({ status: 401, title: "Unauthorized" }), {
    status: 401,
    headers: { "content-type": "application/problem+json" },
  });

function Badge() {
  const unread = useUnreadCount();
  return (
    <span data-testid="unread">
      {unread.isError ? "failed" : String(unread.data ?? "-")}
    </span>
  );
}

let fetchSpy: MockInstance<typeof fetch>;

/** Signed in, then away for ten minutes: the token expired, and no timer ran meanwhile. */
async function returningWithAnExpiredToken(): Promise<void> {
  renewOnce.mockResolvedValueOnce(aSession(300));
  fetchSpy.mockResolvedValueOnce(unreadCount(2));
  render(
    <AuthProvider>
      <QueryClientProvider
        client={
          new QueryClient({
            defaultOptions: { queries: { retry: false, staleTime: 30_000 } },
          })
        }
      >
        <ToastViewport />
        <MemoryRouter>
          <AuthGuard>
            <Badge />
            <textarea aria-label="draft" defaultValue="half a post" />
          </AuthGuard>
        </MemoryRouter>
      </QueryClientProvider>
    </AuthProvider>,
  );
  await waitFor(() =>
    expect(screen.getByTestId("unread")).toHaveTextContent("2"),
  );
  vi.setSystemTime(Date.now() + 10 * 60_000);
}

/** Back in the foreground: the renewal waits on the provider while the badge's refetch is refused. */
async function backInTheForeground(renewal: Promise<unknown>): Promise<void> {
  renewOnce.mockReturnValueOnce(renewal);
  fetchSpy.mockResolvedValueOnce(refused());
  act(() => {
    document.dispatchEvent(new Event("visibilitychange", { bubbles: true }));
  });
  await waitFor(() =>
    expect(screen.getByTestId("unread")).toHaveTextContent("failed"),
  );
}

/** An answer that arrives when the test says so. */
function pending<T = unknown>() {
  let answer: (value: T) => void = () => undefined;
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  fetchSpy = vi.spyOn(globalThis, "fetch");
});

afterEach(() => {
  setAccessToken(undefined);
  for (const toast of [...toastQueue.visibleToasts]) {
    toastQueue.close(toast.key);
  }
  vi.restoreAllMocks();
  vi.clearAllMocks();
  vi.useRealTimers();
  localStorage.clear();
});

describe("a tab returning with an expired token", () => {
  it.each([
    ["the tab is shown again", document, "visibilitychange"],
    ["the device is back online", window, "online"],
  ] as const)(
    "holds a 401 that lands once %s, after the renewal began and before React rendered it",
    async (_, target, event) => {
      // given
      await returningWithAnExpiredToken();
      renewOnce.mockReturnValueOnce(pending().promise);
      fetchSpy.mockResolvedValue(unreadCount(2));

      // when the refusal is already queued behind the event
      target.dispatchEvent(new Event(event));
      notifyUnauthorized("an-access-token");
      await act(async () => {
        await Promise.resolve();
      });

      // then
      expect(startLogin).not.toHaveBeenCalled();
      expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
        "half a post",
      );
    },
  );

  it("keeps the page when the badge is refused before the renewal finds the provider down", async () => {
    // given
    await returningWithAnExpiredToken();
    const { promise: renewal, answer } = pending();
    await backInTheForeground(renewal);

    // when
    await act(async () => {
      answer(unavailable());
      await renewal;
    });

    // then
    expect(startLogin).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
    expect(
      await screen.findByText("Sign-in is unavailable. Retrying."),
    ).toBeInTheDocument();
  });

  it("starts a login once the renewal it waited for is refused", async () => {
    // given
    await returningWithAnExpiredToken();
    const { promise: renewal, answer } = pending();
    await backInTheForeground(renewal);

    // when
    await act(async () => {
      answer(null);
      await renewal;
    });

    // then
    await waitFor(() => expect(startLogin).toHaveBeenCalledOnce());
  });

  it("carries on and reloads the refused badge once the renewal brings a fresh token", async () => {
    // given
    await returningWithAnExpiredToken();
    const { promise: renewal, answer } = pending();
    await backInTheForeground(renewal);
    fetchSpy.mockResolvedValueOnce(unreadCount(5));

    // when
    await act(async () => {
      answer(aSession(300));
      await renewal;
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("unread")).toHaveTextContent("5"),
    );
    expect(startLogin).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
    expect(
      screen.queryByText("Sign-in is unavailable. Retrying."),
    ).not.toBeInTheDocument();
  });

  it("carries on when the badge's 401 for the expired token lands after the fresh token", async () => {
    // given
    await returningWithAnExpiredToken();
    const refusal = pending<Response>();
    fetchSpy
      .mockReturnValueOnce(refusal.promise)
      .mockResolvedValueOnce(unreadCount(5));
    renewOnce.mockResolvedValueOnce(aSession(300, "a-fresh-token"));
    act(() => {
      document.dispatchEvent(new Event("visibilitychange", { bubbles: true }));
    });
    await waitFor(() => expect(getAccessToken()).toBe("a-fresh-token"));

    // when
    await act(async () => {
      refusal.answer(refused());
      await Promise.resolve();
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("unread")).toHaveTextContent("5"),
    );
    expect(fetchSpy.mock.calls[1]?.[1]?.headers).toMatchObject({
      authorization: "Bearer an-access-token",
    });
    expect(startLogin).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
  });

  it("keeps the fresh token when a 401 for the expired one lands after the renewal answered and before React rendered it", async () => {
    // given
    await returningWithAnExpiredToken();
    const { promise: renewal, answer } = pending();
    renewOnce.mockReturnValueOnce(renewal);
    fetchSpy.mockResolvedValue(unreadCount(5));
    act(() => {
      document.dispatchEvent(new Event("visibilitychange", { bubbles: true }));
    });

    // when the renewal answers, and the refusal follows in the same task
    answer(aSession(300, "a-fresh-token"));
    await Promise.resolve();
    await Promise.resolve();
    notifyUnauthorized("an-access-token");
    await act(async () => {
      await Promise.resolve();
    });

    // then
    await waitFor(() => expect(getAccessToken()).toBe("a-fresh-token"));
    expect(startLogin).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
  });
});
