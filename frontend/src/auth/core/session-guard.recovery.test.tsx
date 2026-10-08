import { QueryClient, useQuery } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthContextProps } from "@/auth/core/session";
import {
  authState as aSession,
  KEPT_NOTICE,
  Refusal,
  refuse,
  ShellGuard,
} from "@/test/session-guard-shell";
import { inQueryClient } from "@/test/session-probe";

const mockUseAuth = vi.fn<() => AuthContextProps>();

/** Answers from the state the guard last read, as the session answers from its own. */
function holdsRefusals(): boolean {
  const current = mockUseAuth.mock.results.at(-1)?.value as
    { signinUnavailable?: boolean; renewalPending?: boolean } | undefined;
  return (
    Boolean(current?.signinUnavailable) || Boolean(current?.renewalPending)
  );
}

function authState(overrides: Partial<AuthContextProps>) {
  return aSession({
    user: { access_token: "expired-token", profile: { sub: "alice-sub" } },
    holdsRefusals,
    ...overrides,
  });
}

const recovered = () =>
  authState({
    user: { access_token: "fresh-token", profile: { sub: "alice-sub" } },
  });

const refusedRead = vi.fn();
const failedRead = vi.fn();
const signIn = vi.fn();

function Reads() {
  const refused = useQuery({ queryKey: ["refused"], queryFn: refusedRead });
  const failed = useQuery({ queryKey: ["failed"], queryFn: failedRead });
  return (
    <>
      <span data-testid="refused">{refused.status}</span>
      <span data-testid="failed">{failed.status}</span>
    </>
  );
}

function Guarded() {
  return (
    <ShellGuard auth={mockUseAuth()} signIn={signIn}>
      <Reads />
      <textarea aria-label="draft" defaultValue="half a post" />
    </ShellGuard>
  );
}

const guarded = () => <Guarded />;

const refusal = () => new Refusal("Unauthorized");

/** One read refused for want of a token and one that failed on its own, during an outage. */
async function readsFailedDuringOutage(
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  refusedRead.mockRejectedValueOnce(refusal()).mockResolvedValue("fresh");
  failedRead.mockRejectedValue(new Error("Server error"));
  mockUseAuth.mockReturnValue(authState({ signinUnavailable: true }));
  const view = render(guarded(), inQueryClient(client));
  await waitFor(() => {
    expect(screen.getByTestId("refused")).toHaveTextContent("error");
    expect(screen.getByTestId("failed")).toHaveTextContent("error");
  });
  return view;
}

afterEach(() => {
  vi.resetAllMocks();
});

describe("the session guard, while a renewal for an expired token is under way", () => {
  it("holds a 401 rather than leaving for a sign-in", () => {
    // given
    const auth = authState({ renewalPending: true });
    mockUseAuth.mockReturnValue(auth);
    refusedRead.mockResolvedValue("ok");
    failedRead.mockResolvedValue("ok");
    render(guarded(), inQueryClient());

    // when
    act(() => {
      refuse("expired-token");
    });

    // then
    expect(auth.removeUser).not.toHaveBeenCalled();
    expect(signIn).not.toHaveBeenCalled();
    expect(screen.queryByText(KEPT_NOTICE)).not.toBeInTheDocument();
  });

  it("keeps the page and says nothing once that renewal brings a fresh token", async () => {
    // given
    const during = authState({ renewalPending: true });
    mockUseAuth.mockReturnValue(during);
    refusedRead.mockResolvedValue("ok");
    failedRead.mockResolvedValue("ok");
    const { rerender } = render(guarded(), inQueryClient());
    act(() => {
      refuse("expired-token");
    });

    // when
    const auth = recovered();
    mockUseAuth.mockReturnValue(auth);
    rerender(guarded());

    // then
    await expect(
      screen.findByText(KEPT_NOTICE, {}, { timeout: 200 }),
    ).rejects.toThrow();
    expect(during.removeUser).not.toHaveBeenCalled();
    expect(auth.removeUser).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
  });

  it("keeps the held 401 and says so once that renewal finds the provider down", async () => {
    // given
    mockUseAuth.mockReturnValue(authState({ renewalPending: true }));
    refusedRead.mockResolvedValue("ok");
    failedRead.mockResolvedValue("ok");
    const { rerender } = render(guarded(), inQueryClient());
    act(() => {
      refuse("expired-token");
    });

    // when
    mockUseAuth.mockReturnValue(authState({ signinUnavailable: true }));
    rerender(guarded());

    // then
    expect(await screen.findByText(KEPT_NOTICE)).toBeInTheDocument();
  });
});

describe("the session guard, once a renewal succeeds after an outage", () => {
  it("reloads only the reads refused for want of a token", async () => {
    // given
    const { rerender } = await readsFailedDuringOutage();
    act(() => {
      refuse("expired-token");
    });

    // when
    mockUseAuth.mockReturnValue(recovered());
    rerender(guarded());

    // then
    await waitFor(() =>
      expect(screen.getByTestId("refused")).toHaveTextContent("success"),
    );
    expect(refusedRead).toHaveBeenCalledTimes(2);
    expect(failedRead).toHaveBeenCalledOnce();
  });

  it("reloads nothing on a later recovery that held no 401", async () => {
    // given one outage whose refused read was reloaded
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const { rerender } = await readsFailedDuringOutage(client);
    act(() => {
      refuse("expired-token");
    });
    mockUseAuth.mockReturnValue(recovered());
    rerender(guarded());
    await waitFor(() =>
      expect(screen.getByTestId("refused")).toHaveTextContent("success"),
    );
    // and a second outage that refused the read again without a 401 held
    mockUseAuth.mockReturnValue(
      authState({ signinUnavailable: true, user: recovered().user }),
    );
    rerender(guarded());
    refusedRead.mockRejectedValueOnce(refusal());
    await act(async () => {
      await client.refetchQueries({ queryKey: ["refused"] });
    });
    await waitFor(() =>
      expect(screen.getByTestId("refused")).toHaveTextContent("error"),
    );

    // when
    mockUseAuth.mockReturnValue(recovered());
    rerender(guarded());

    // then
    await expect(
      waitFor(() => expect(refusedRead).toHaveBeenCalledTimes(4), {
        timeout: 200,
      }),
    ).rejects.toThrow();
  });

  it("keeps the fresh token and reloads the read when a 401 for the expired one lands late", async () => {
    // given an outage that ended before the refusal of a read sent meanwhile
    const { rerender } = await readsFailedDuringOutage();
    const auth = recovered();
    mockUseAuth.mockReturnValue(auth);
    rerender(guarded());

    // when
    act(() => {
      refuse("expired-token");
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("refused")).toHaveTextContent("success"),
    );
    expect(auth.removeUser).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
  });

  it("asks a read refused for an older token again at once, outage or not, and raises no notice", async () => {
    // given
    await readsFailedDuringOutage();

    // when
    act(() => {
      refuse("an-older-token");
    });

    // then
    await waitFor(() =>
      expect(screen.getByTestId("refused")).toHaveTextContent("success"),
    );
    await expect(
      screen.findByText(KEPT_NOTICE, {}, { timeout: 200 }),
    ).rejects.toThrow();
  });

  it("reloads nothing when no request was refused meanwhile", async () => {
    // given
    const { rerender } = await readsFailedDuringOutage();

    // when
    mockUseAuth.mockReturnValue(recovered());
    rerender(guarded());

    // then
    await expect(
      waitFor(() => expect(refusedRead).toHaveBeenCalledTimes(2), {
        timeout: 200,
      }),
    ).rejects.toThrow();
  });
});
