import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMySaved,
  useSave,
  useUnsave,
} from "@/features/bookmarks/api/use-save";

vi.mock("@/features/bookmarks/adapters/http-save-gateway", () => ({
  httpSaveGateway: {
    getState: vi.fn(),
    save: vi.fn(),
    unsave: vi.fn(),
    markHelpful: vi.fn(),
    listMine: vi.fn(),
  },
}));
const notify = vi.fn();
vi.mock("@/components/ui/toast", () => ({
  notify: (title: string, action?: unknown) => {
    notify(title, action);
  },
  notifyError: vi.fn(),
}));

import { httpSaveGateway } from "@/features/bookmarks/adapters/http-save-gateway";

const gateway = vi.mocked(httpSaveGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useSave", () => {
  beforeEach(() => vi.clearAllMocks());

  it("saves via the gateway and raises a toast", async () => {
    gateway.save.mockResolvedValue({
      viewerHasSaved: true,
      viewerHelped: null,
    });
    const onView = vi.fn();
    const ref = { kind: "artifact" as const, id: 100 };
    const { result } = renderHook(() => useSave(ref, onView), { wrapper });

    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.save).toHaveBeenCalledWith(ref);
    expect(notify).toHaveBeenCalledWith("Saved", expect.anything());
  });
});

describe("useUnsave", () => {
  beforeEach(() => vi.clearAllMocks());

  it("removes via the gateway and offers an Undo toast that re-saves", async () => {
    gateway.unsave.mockResolvedValue(undefined);
    gateway.save.mockResolvedValue({
      viewerHasSaved: true,
      viewerHelped: null,
    });
    const ref = { kind: "artifact" as const, id: 100 };
    const { result } = renderHook(() => useUnsave(ref), { wrapper });

    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.unsave).toHaveBeenCalledWith(ref);
    expect(notify).toHaveBeenCalledWith(
      "Removed",
      expect.objectContaining({ label: "Undo" }),
    );

    const action = notify.mock.calls[0]![1] as { onPress: () => void };
    action.onPress();
    await waitFor(() => expect(gateway.save).toHaveBeenCalledWith(ref));
  });
});

describe("useMySaved", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists the personal library", async () => {
    gateway.listMine.mockResolvedValue([]);
    const { result } = renderHook(() => useMySaved(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.listMine).toHaveBeenCalled();
  });
});
