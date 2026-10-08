import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { artifactKeys } from "@/features/knowledge/api/query-keys";
import {
  useArtifacts,
  useCaptureArtifact,
  useChangeContributor,
  useEditArtifact,
  useRemoveArtifact,
} from "@/features/knowledge/api/use-artifacts";
import { recognitionKeys } from "@/features/recognition/api/query-keys";

vi.mock("@/features/knowledge/adapters/http-knowledge-gateway", () => ({
  httpKnowledgeGateway: {
    search: vi.fn(),
    capture: vi.fn(),
    edit: vi.fn(),
    changeContributor: vi.fn(),
    remove: vi.fn(),
  },
}));

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const gateway = vi.mocked(httpKnowledgeGateway);

const artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal talk",
  description: "",
  url: "https://example.com/talk",
  tags: ["architecture"],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE" as const,
  createdAt: "x",
  updatedAt: "x",
};

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useArtifacts", () => {
  it("returns the library page for the given params", async () => {
    // given
    gateway.search.mockResolvedValue({
      content: [artifact],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    // when
    const { result } = renderHook(
      () => useArtifacts("backend-guild", { page: 0, size: 20 }),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
    expect(gateway.search.mock.calls[0]).toEqual([
      "backend-guild",
      { page: 0, size: 20 },
    ]);
  });
});

describe("useCaptureArtifact", () => {
  it("captures an artifact via the gateway", async () => {
    // given
    gateway.capture.mockResolvedValue(artifact);
    const { result } = renderHook(() => useCaptureArtifact("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({
      title: "Hexagonal talk",
      typeId: 3,
      url: "https://example.com/talk",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.capture.mock.calls[0]).toEqual([
      "backend-guild",
      {
        title: "Hexagonal talk",
        typeId: 3,
        url: "https://example.com/talk",
      },
    ]);
  });

  it("marks stale every read the credited contributor appears in", async () => {
    // given
    const reads = [
      artifactKeys.community("backend-guild"),
      expertiseKeys.community("backend-guild"),
      expertiseKeys.selfView(),
      expertiseKeys.areaEvidence("terraform"),
      recognitionKeys.myContributions(),
    ];
    for (const queryKey of reads) client.setQueryData(queryKey, {});
    gateway.capture.mockResolvedValue(artifact);
    const { result } = renderHook(() => useCaptureArtifact("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({
      title: "Hexagonal talk",
      typeId: 3,
      url: "https://example.com/talk",
      contributor: "bob",
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    for (const queryKey of reads) {
      expect(client.getQueryState(queryKey)?.isInvalidated).toBe(true);
    }
  });
});

describe("useEditArtifact", () => {
  it("edits an artifact via the gateway", async () => {
    // given
    gateway.edit.mockResolvedValue({ ...artifact, title: "Updated talk" });
    const { result } = renderHook(() => useEditArtifact("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({
      id: 1,
      input: {
        title: "Updated talk",
        typeId: 3,
        url: "https://example.com/talk",
      },
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.edit.mock.calls[0]).toEqual([
      1,
      { title: "Updated talk", typeId: 3, url: "https://example.com/talk" },
    ]);
  });
});

describe("useChangeContributor", () => {
  it("changes the contributor via the gateway", async () => {
    // given
    gateway.changeContributor.mockResolvedValue({
      ...artifact,
      contributorUserId: "bob",
    });
    const { result } = renderHook(() => useChangeContributor("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ id: 1, contributor: "bob" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.changeContributor).toHaveBeenCalledWith(1, "bob");
  });

  it("marks stale every read that names the contributor", async () => {
    // given
    const reads = [
      artifactKeys.community("backend-guild"),
      expertiseKeys.community("backend-guild"),
      expertiseKeys.selfView(),
      expertiseKeys.areaEvidence("terraform"),
      recognitionKeys.myContributions(),
    ];
    for (const queryKey of reads) client.setQueryData(queryKey, {});
    gateway.changeContributor.mockResolvedValue({
      ...artifact,
      contributorUserId: "bob",
    });
    const { result } = renderHook(() => useChangeContributor("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ id: 1, contributor: "bob" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    for (const queryKey of reads) {
      expect(client.getQueryState(queryKey)?.isInvalidated).toBe(true);
    }
  });
});

describe("useRemoveArtifact", () => {
  it("removes an artifact via the gateway", async () => {
    // given
    gateway.remove.mockResolvedValue(undefined);
    const { result } = renderHook(() => useRemoveArtifact("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate(1);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.remove.mock.calls[0]).toEqual([1]);
  });
});
