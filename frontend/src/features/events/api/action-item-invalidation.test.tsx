import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { actionItemKeys } from "@/features/events/api/query-keys";
import {
  useDeleteActionItem,
  useEditActionItem,
  useRaiseActionItem,
  useSettleActionItem,
} from "@/features/events/api/use-action-items";
import { anActionItem } from "@/test/action-item-fixture";

/**
 * Every action-item write changes a list the community is already looking at, and the list is only
 * ever refetched on invalidation. Asserting the gateway call alone leaves that silent: the write
 * lands, the tab keeps rendering the page it fetched before it, and the item appears to have not
 * moved at all.
 */

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    raiseActionItem: vi.fn(),
    editActionItem: vi.fn(),
    deleteActionItem: vi.fn(),
    closeActionItem: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function trackedClient() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(client, "invalidateQueries");
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { invalidateSpy, wrapper };
}

const actionItems = { queryKey: actionItemKeys.community("backend-guild") };

beforeEach(() => vi.clearAllMocks());

describe("useRaiseActionItem", () => {
  it("invalidates the community's action items the raise just added to", async () => {
    // given
    gateway.raiseActionItem.mockResolvedValue(anActionItem());
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useRaiseActionItem("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ title: "Write the runbook" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useEditActionItem", () => {
  it("invalidates the community's action items the edit just rewrote", async () => {
    // given
    gateway.editActionItem.mockResolvedValue(anActionItem());
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useEditActionItem("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ id: 101, input: { title: "Renamed" } });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useDeleteActionItem", () => {
  it("invalidates the community's action items the delete just removed from", async () => {
    // given
    gateway.deleteActionItem.mockResolvedValue(undefined);
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useDeleteActionItem("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate(101);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useSettleActionItem", () => {
  it("invalidates the community's action items after it settles one", async () => {
    // given
    gateway.closeActionItem.mockResolvedValue(
      anActionItem({ outcome: "DONE" }),
    );
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(
      () => useSettleActionItem("backend-guild", 101),
      { wrapper },
    );

    // when
    result.current.mutate("DONE");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});
