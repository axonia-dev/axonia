import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { httpMailServerGateway } from "@/selfhosted/features/mail-server/adapters/http-mail-server-gateway";
import { mailServerKeys } from "@/selfhosted/features/mail-server/api/query-keys";
import {
  useClearMailServer,
  useMailServerChanges,
  useSaveMailServer,
  useTestMailServer,
} from "@/selfhosted/features/mail-server/api/use-mail-server";
import {
  ACCEPTED,
  CONFIGURED,
  FROM_NAME_CHANGE,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";

vi.mock(
  "@/selfhosted/features/mail-server/adapters/http-mail-server-gateway",
  () => ({
    httpMailServerGateway: {
      read: vi.fn(),
      isConfigured: vi.fn(),
      test: vi.fn(),
      save: vi.fn(),
      clear: vi.fn(),
      changes: vi.fn(),
    },
  }),
);

const gateway = vi.mocked(httpMailServerGateway);

function harness() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidated = vi.spyOn(client, "invalidateQueries");
  function wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  }
  return { client, invalidated, wrapper };
}

function keysOf(spy: ReturnType<typeof harness>["invalidated"]): unknown[] {
  return spy.mock.calls.map(([filters]) => filters?.queryKey);
}

const SAVE = {
  values: {
    host: "smtp.acme.com",
    port: 587,
    security: "STARTTLS",
    username: "axonia",
    password: null,
    fromAddress: "axonia@acme.com",
    fromName: null,
  },
  proof: { testToken: ACCEPTED.testToken, baseRevision: 3 },
} as const;

const TYPED = { ...SAVE.values, password: "s3cret" };

function page(number: number) {
  return {
    content: [FROM_NAME_CHANGE],
    totalElements: 21,
    totalPages: 2,
    number,
    size: 20,
  };
}

describe("the mail server's queries", () => {
  it("put a saved view in place, and ask again whether mail is configured and for the history", async () => {
    // given
    const { client, invalidated, wrapper } = harness();
    gateway.save.mockResolvedValue(CONFIGURED);
    const { result } = renderHook(() => useSaveMailServer(), { wrapper });

    // when
    await act(() => result.current.mutateAsync(SAVE));

    // then
    expect(client.getQueryData(mailServerKeys.view())).toEqual(CONFIGURED);
    expect(keysOf(invalidated)).toEqual([
      mailServerKeys.status(),
      mailServerKeys.changes(),
    ]);
  });

  it("ask for the mail server again when a save finds it changed by someone else", async () => {
    // given
    const { invalidated, wrapper } = harness();
    gateway.save.mockRejectedValue(
      new ApiError(409, "Reload it.", "stale_revision", "Reload it."),
    );
    const { result } = renderHook(() => useSaveMailServer(), { wrapper });

    // when
    await act(() => result.current.mutateAsync(SAVE).catch(() => undefined));

    // then
    expect(keysOf(invalidated)).toEqual([mailServerKeys.view()]);
  });

  it("ask again for everything about the mail server once it is cleared", async () => {
    // given
    const { invalidated, wrapper } = harness();
    gateway.clear.mockResolvedValue(undefined);
    const { result } = renderHook(() => useClearMailServer(), { wrapper });

    // when
    await act(() => result.current.mutateAsync(3));

    // then
    expect(gateway.clear).toHaveBeenCalledWith(3);
    expect(keysOf(invalidated)).toEqual([mailServerKeys.all]);
  });

  it("ask for the mail server again when a clear finds it changed by someone else", async () => {
    // given
    const { invalidated, wrapper } = harness();
    gateway.clear.mockRejectedValue(
      new ApiError(409, "Reload it.", "stale_revision", "Reload it."),
    );
    const { result } = renderHook(() => useClearMailServer(), { wrapper });

    // when
    await act(() => result.current.mutateAsync(3).catch(() => undefined));

    // then
    expect(keysOf(invalidated)).toEqual([mailServerKeys.view()]);
  });

  it("page the history until its last page", async () => {
    // given
    const { wrapper } = harness();
    gateway.changes.mockImplementation((n: number) => Promise.resolve(page(n)));
    const { result } = renderHook(() => useMailServerChanges(), { wrapper });
    await waitFor(() => expect(result.current.hasNextPage).toBe(true));

    // when
    await act(() => result.current.fetchNextPage());

    // then
    await waitFor(() => expect(result.current.hasNextPage).toBe(false));
    expect(gateway.changes.mock.calls).toEqual([[0], [1]]);
  });

  it("forget a test's values, a typed password among them, once the form is gone", async () => {
    // given
    const { client, wrapper } = harness();
    gateway.test.mockResolvedValue(ACCEPTED);
    const { result, unmount } = renderHook(() => useTestMailServer(), {
      wrapper,
    });
    await act(() =>
      result.current.mutateAsync({ values: TYPED, recipient: "ana@acme.com" }),
    );

    // when
    unmount();

    // then
    await waitFor(() => expect(client.getMutationCache().getAll()).toEqual([]));
  });

  it("forget a save's values, a typed password among them, once the form is gone", async () => {
    // given
    const { client, wrapper } = harness();
    gateway.save.mockResolvedValue(CONFIGURED);
    const { result, unmount } = renderHook(() => useSaveMailServer(), {
      wrapper,
    });
    await act(() => result.current.mutateAsync({ ...SAVE, values: TYPED }));

    // when
    unmount();

    // then
    await waitFor(() => expect(client.getMutationCache().getAll()).toEqual([]));
  });
});
