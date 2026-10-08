import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateSectionType,
  useDeleteSectionType,
  useSectionTypes,
  useUpdateSectionType,
} from "@/features/events/api/use-section-types";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listSectionTypes: vi.fn(),
    createSectionType: vi.fn(),
    updateSectionType: vi.fn(),
    deleteSectionType: vi.fn(),
  },
}));

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useSectionTypes", () => {
  it("lists section types", async () => {
    gateway.listSectionTypes.mockResolvedValue([
      {
        id: 1,
        label: "Talk",
        expectsPresenter: true,
        icon: null,
        accentColor: null,
      },
    ]);
    const { result } = renderHook(() => useSectionTypes(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
  });
});

describe("useCreateSectionType", () => {
  it("calls createSectionType and returns the new type", async () => {
    const newType = {
      id: 2,
      label: "Workshop",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    };
    gateway.createSectionType.mockResolvedValue(newType);
    gateway.listSectionTypes.mockResolvedValue([]);
    const { result } = renderHook(() => useCreateSectionType(), { wrapper });
    result.current.mutate({
      label: "Workshop",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(newType);
  });
});

describe("useUpdateSectionType", () => {
  it("calls updateSectionType and succeeds", async () => {
    const updated = {
      id: 1,
      label: "Lightning Talk",
      expectsPresenter: true,
      icon: null,
      accentColor: null,
    };
    gateway.updateSectionType.mockResolvedValue(updated);
    gateway.listSectionTypes.mockResolvedValue([]);
    const { result } = renderHook(() => useUpdateSectionType(), { wrapper });
    result.current.mutate({
      id: 1,
      input: {
        label: "Lightning Talk",
        expectsPresenter: true,
        icon: null,
        accentColor: null,
      },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(updated);
  });
});

describe("useDeleteSectionType", () => {
  it("calls deleteSectionType and succeeds", async () => {
    gateway.deleteSectionType.mockResolvedValue(undefined);
    gateway.listSectionTypes.mockResolvedValue([]);
    const { result } = renderHook(() => useDeleteSectionType(), { wrapper });
    result.current.mutate({ id: 1 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("calls deleteSectionType with reassignToId", async () => {
    gateway.deleteSectionType.mockResolvedValue(undefined);
    gateway.listSectionTypes.mockResolvedValue([]);
    const { result } = renderHook(() => useDeleteSectionType(), { wrapper });
    result.current.mutate({ id: 1, reassignToId: 2 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
