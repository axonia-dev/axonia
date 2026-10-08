import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";

vi.mock("@/features/identity/adapters/http-identity-gateway", () => ({
  httpIdentityGateway: { me: vi.fn(), resolve: vi.fn() },
}));

const gateway = vi.mocked(httpIdentityGateway);

const STALE_TIME = 30_000;

let client: QueryClient;

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: STALE_TIME } },
  });
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function person(id: string, displayName = "Alice Anderson"): User {
  return {
    id,
    displayName,
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  };
}

function cacheBatch(people: User[], ageMs = 0) {
  client.setQueryData(identityKeys.users(people.map(({ id }) => id)), people, {
    updatedAt: Date.now() - ageMs,
  });
}

async function settle() {
  await act(async () => {});
}

describe("useUsers seeded from earlier batches", () => {
  /** A dialog naming one person from a list already on screen must not show a placeholder name
   *  while it asks the server again for somebody the page has resolved. */
  it("answers at once for people an earlier batch already resolved", () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2")]);
    gateway.resolve.mockReturnValue(new Promise(() => {}));

    // when
    const { result } = renderHook(() => useUsers(["sub-2"]), { wrapper });

    // then
    expect(result.current.data?.["sub-2"]?.displayName).toBe("Alice Anderson");
  });

  it("waits for the server when an earlier batch resolved only some of the people", () => {
    // given
    cacheBatch([person("sub-1")]);
    gateway.resolve.mockReturnValue(new Promise(() => {}));

    // when
    const { result } = renderHook(() => useUsers(["sub-1", "sub-2"]), {
      wrapper,
    });

    // then
    expect(result.current.data).toBeUndefined();
  });

  it("does not look through the cache for an empty id list", () => {
    // given
    cacheBatch([person("sub-1")]);
    const findAll = vi.spyOn(client.getQueryCache(), "findAll");

    // when
    renderHook(() => useUsers([]), { wrapper });

    // then
    expect(findAll).not.toHaveBeenCalled();
  });

  it("looks through the cache once for a new batch", () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2")]);
    gateway.resolve.mockReturnValue(new Promise(() => {}));
    const findAll = vi.spyOn(client.getQueryCache(), "findAll");

    // when
    renderHook(() => useUsers(["sub-2"]), { wrapper });

    // then
    const scans = findAll.mock.calls.filter(
      ([filters]) => filters?.queryKey === identityKeys.anyUsers,
    );
    expect(scans).toHaveLength(1);
  });

  it("does not refetch a seed taken from a batch resolved within the stale time", async () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2")]);

    // when
    renderHook(() => useUsers(["sub-2"]), { wrapper });
    await settle();

    // then
    expect(gateway.resolve).not.toHaveBeenCalled();
  });

  it("refetches a seed as old as the batch it was taken from", async () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2")], STALE_TIME + 1_000);
    gateway.resolve.mockResolvedValue([person("sub-2", "Alice Renamed")]);

    // when
    const { result } = renderHook(() => useUsers(["sub-2"]), { wrapper });

    // then
    await waitFor(() =>
      expect(result.current.data?.["sub-2"]?.displayName).toBe("Alice Renamed"),
    );
    expect(gateway.resolve).toHaveBeenCalledWith(["sub-2"]);
  });

  /** Two batches can hold the same person. The copy resolved last is the truth, wherever its
   *  batch sits in the cache, and an older copy it replaces does not age the seed. */
  it("takes each person from the batch that resolved them most recently", async () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2", "Alice Renamed")]);
    cacheBatch(
      [person("sub-2", "Alice Anderson"), person("sub-3")],
      STALE_TIME + 1_000,
    );

    // when
    const { result } = renderHook(() => useUsers(["sub-2"]), { wrapper });
    await settle();

    // then
    expect(result.current.data?.["sub-2"]?.displayName).toBe("Alice Renamed");
    expect(gateway.resolve).not.toHaveBeenCalled();
  });

  it("resets to its seed, and asks the server again", async () => {
    // given
    cacheBatch([person("sub-1"), person("sub-2")]);
    const { result } = renderHook(() => useUsers(["sub-2"]), { wrapper });
    gateway.resolve.mockResolvedValue([person("sub-2", "Alice Renamed")]);
    await act(() =>
      client.refetchQueries({ queryKey: identityKeys.users(["sub-2"]) }),
    );
    await waitFor(() =>
      expect(result.current.data?.["sub-2"]?.displayName).toBe("Alice Renamed"),
    );
    gateway.resolve.mockReturnValue(new Promise(() => {}));

    // when
    act(() => {
      void client.resetQueries({ queryKey: identityKeys.users(["sub-2"]) });
    });

    // then
    await waitFor(() =>
      expect(result.current.data?.["sub-2"]?.displayName).toBe(
        "Alice Anderson",
      ),
    );
    expect(result.current.fetchStatus).toBe("fetching");
  });
});
