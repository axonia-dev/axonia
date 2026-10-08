import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";

vi.mock("@/features/knowledge/adapters/http-knowledge-gateway", () => ({
  httpKnowledgeGateway: { listTypes: vi.fn() },
}));

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const gateway = vi.mocked(httpKnowledgeGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useArtifactTypes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the artifact-type vocabulary", async () => {
    // given
    gateway.listTypes.mockResolvedValue([
      {
        id: 3,
        label: "Recording",
        icon: null,
        accentColor: null,
        artifactCount: 0,
      },
    ]);

    // when
    const { result } = renderHook(() => useArtifactTypes(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([
      {
        id: 3,
        label: "Recording",
        icon: null,
        accentColor: null,
        artifactCount: 0,
      },
    ]);
  });
});
