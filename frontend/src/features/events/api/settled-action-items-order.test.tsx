import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useSettledActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { anActionItem } from "@/test/action-item-fixture";

/**
 * The Past tab shows a page the server truncated, in an order the client interleaves. Those two
 * have to key on the same column: while the server truncated on `created_at` and the tab displayed
 * by `updatedAt`, an item raised months ago and ticked done today was never on the fetched page at
 * all, so it never appeared under Past - and the "showing the first N" notice said otherwise.
 */

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { listActionItems: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function pageOf(content: ActionItem[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 20,
  };
}

beforeEach(() => vi.clearAllMocks());

describe("useSettledActionItems", () => {
  it("asks the server for settle order, in both buckets", async () => {
    // given
    gateway.listActionItems.mockResolvedValue(pageOf([anActionItem()]));

    // when
    renderHook(() => useSettledActionItems("backend-guild"), { wrapper });

    // then
    await waitFor(() =>
      expect(gateway.listActionItems).toHaveBeenCalledTimes(2),
    );
    expect(
      gateway.listActionItems.mock.calls.map((call) => [
        call[1].outcome,
        call[1].sort,
      ]),
    ).toEqual(
      expect.arrayContaining([
        ["DONE", "updated_at,desc"],
        ["DROPPED", "updated_at,desc"],
      ]),
    );
  });
});
