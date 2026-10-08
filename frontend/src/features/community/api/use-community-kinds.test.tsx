import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import {
  useCommunityKinds,
  useCreateKind,
  useDeleteKind,
  useUpdateKind,
} from "@/features/community/api/use-community-kinds";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    listKinds: vi.fn(),
    createKind: vi.fn(),
    renameKind: vi.fn(),
    deleteKind: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useCommunityKinds", () => {
  it("returns the tenant's kinds", async () => {
    // given
    gateway.listKinds.mockResolvedValue([
      { id: 1, label: "Guild", communityCount: 0 },
    ]);

    // when
    const { result } = renderHook(() => useCommunityKinds(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([
      { id: 1, label: "Guild", communityCount: 0 },
    ]);
  });
});

describe("useCreateKind", () => {
  it("creates a kind", async () => {
    // given
    gateway.createKind.mockResolvedValue({
      id: 2,
      label: "Tribe",
      communityCount: 0,
    });
    const { result } = renderHook(() => useCreateKind(), { wrapper });

    // when
    result.current.mutate("Tribe");

    // then
    await waitFor(() =>
      expect(gateway.createKind.mock.calls[0]).toEqual(["Tribe"]),
    );
  });
});

describe("useUpdateKind", () => {
  it("renames a kind", async () => {
    // given
    gateway.renameKind.mockResolvedValue({
      id: 1,
      label: "Renamed",
      communityCount: 0,
    });
    const { result } = renderHook(() => useUpdateKind(), { wrapper });

    // when
    result.current.mutate({ id: 1, label: "Renamed" });

    // then
    await waitFor(() =>
      expect(gateway.renameKind.mock.calls[0]).toEqual([1, "Renamed"]),
    );
  });
});

describe("useDeleteKind", () => {
  it("deletes a kind with reassignment", async () => {
    // given
    gateway.deleteKind.mockResolvedValue();
    const { result } = renderHook(() => useDeleteKind(), { wrapper });

    // when
    result.current.mutate({ id: 1, reassignToId: 2 });

    // then
    await waitFor(() =>
      expect(gateway.deleteKind.mock.calls[0]).toEqual([1, 2]),
    );
  });
});
