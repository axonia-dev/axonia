import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { artifactKeys } from "@/features/knowledge/api/query-keys";
import {
  useAttachResource,
  useDetachResource,
  useSessionResources,
} from "@/features/knowledge/api/use-session-resources";

vi.mock("@/features/knowledge/adapters/http-knowledge-gateway", () => ({
  httpKnowledgeGateway: {
    sessionResources: vi.fn(),
    attachResource: vi.fn(),
    detachResource: vi.fn(),
  },
}));

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const gateway = vi.mocked(httpKnowledgeGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("session-resource hooks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads the session's resources", async () => {
    gateway.sessionResources.mockResolvedValue([]);
    const { result } = renderHook(() => useSessionResources("eng", 100), {
      wrapper,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.sessionResources.mock.calls[0]).toEqual(["eng", 100]);
  });

  it("attaches via the gateway", async () => {
    gateway.attachResource.mockResolvedValue({
      id: 1,
      artifactId: 9,
      sessionId: 100,
      segmentId: 7,
      attachedByUserId: "alice",
      createdAt: "x",
    });
    const { result } = renderHook(() => useAttachResource("eng", 100), {
      wrapper,
    });
    result.current.mutate({ segmentId: 7, artifactId: 9 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.attachResource.mock.calls[0]).toEqual([
      "eng",
      100,
      { segmentId: 7, artifactId: 9 },
    ]);
  });

  it("detaches by artifact and segment via the gateway", async () => {
    gateway.detachResource.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDetachResource("eng", 100), {
      wrapper,
    });
    result.current.mutate({ artifactId: 9, segmentId: 7 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.detachResource.mock.calls[0]).toEqual([
      "eng",
      100,
      { artifactId: 9, segmentId: 7 },
    ]);
  });

  it("invalidates the library cache a detach may have changed", async () => {
    gateway.detachResource.mockResolvedValue(undefined);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useDetachResource("eng", 100), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });
    result.current.mutate({ artifactId: 9, segmentId: 7 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: artifactKeys.community("eng"),
    });
  });
});
