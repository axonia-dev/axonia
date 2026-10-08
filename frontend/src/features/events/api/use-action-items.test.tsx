import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { actionItemKeys } from "@/features/events/api/query-keys";
import {
  nextOutcome,
  useActionItems,
  useDeleteActionItem,
  useEditActionItem,
  useRaiseActionItem,
  useSettleActionItem,
  useSettledActionItems,
} from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listActionItems: vi.fn(),
    raiseActionItem: vi.fn(),
    editActionItem: vi.fn(),
    deleteActionItem: vi.fn(),
    dropActionItem: vi.fn(),
    closeActionItem: vi.fn(),
    reopenActionItem: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

const done = anActionItem({
  id: 1,
  outcome: "DONE",
  updatedAt: "2026-09-10T09:00:00Z",
});
const dropped = anActionItem({
  id: 2,
  outcome: "DROPPED",
  updatedAt: "2026-09-18T09:00:00Z",
});

function pageOf(content: ActionItem[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    number: 0,
    size: 20,
  };
}

describe("useActionItems", () => {
  it("returns the action item page", async () => {
    gateway.listActionItems.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });

    const { result } = renderHook(
      () => useActionItems("backend-guild", { page: 0, size: 20 }),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useRaiseActionItem", () => {
  it("raises through the gateway", async () => {
    gateway.raiseActionItem.mockResolvedValue({} as never);
    const { result } = renderHook(() => useRaiseActionItem("backend-guild"), {
      wrapper,
    });

    result.current.mutate({ title: "Close the Jira ticket" });

    await waitFor(() =>
      expect(gateway.raiseActionItem.mock.calls[0]).toEqual([
        "backend-guild",
        { title: "Close the Jira ticket" },
      ]),
    );
  });
});

describe("useEditActionItem", () => {
  it("edits through the gateway, carrying the re-pinned checkpoint", async () => {
    // given
    gateway.editActionItem.mockResolvedValue({} as never);
    const { result } = renderHook(() => useEditActionItem("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate({
      id: 7,
      input: { title: "Renew the certificate", checkpointSessionId: 12 },
    });

    // then
    await waitFor(() =>
      expect(gateway.editActionItem.mock.calls[0]).toEqual([
        7,
        { title: "Renew the certificate", checkpointSessionId: 12 },
      ]),
    );
  });
});

describe("useDeleteActionItem", () => {
  it("deletes through the gateway", async () => {
    // given
    gateway.deleteActionItem.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDeleteActionItem("backend-guild"), {
      wrapper,
    });

    // when
    result.current.mutate(7);

    // then
    await waitFor(() =>
      expect(gateway.deleteActionItem).toHaveBeenCalledWith(7),
    );
  });
});

describe("useSettledActionItems", () => {
  /** `outcome` narrows to one bucket per request, so a settled list that asked for one of the two
   *  would silently hide every item that ended the other way. */
  it("reads done and dropped as two pages and answers with both", async () => {
    // given
    gateway.listActionItems.mockImplementation((_slug, params) =>
      Promise.resolve(pageOf(params.outcome === "DONE" ? [done] : [dropped])),
    );

    // when
    const { result } = renderHook(
      () => useSettledActionItems("backend-guild"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.items).toHaveLength(2));
    expect(result.current.items.map((item) => item.id)).toEqual([2, 1]);
    expect(result.current.total).toBe(2);
    expect(
      gateway.listActionItems.mock.calls.map((call) => call[1].outcome),
    ).toEqual(expect.arrayContaining(["DONE", "DROPPED"]));
  });
});

describe("useSettleActionItem", () => {
  const LIST_KEY = actionItemKeys.list("backend-guild", { page: 0, size: 20 });

  const sample: ActionItem = {
    id: 5,
    communityId: 1,
    title: "Close the Jira ticket",
    description: "",
    tags: [],
    raisedByUserId: "u1",
    raisedByDisplayName: "Ada",
    assigneeUserId: null,
    assigneeDisplayName: null,
    dueDate: null,
    originSessionId: null,
    checkpoint: {
      sessionId: 12,
      scheduledAt: "2026-09-24T09:00:00Z",
      pinned: false,
      governingTimeZone: null,
    },
    carryCount: 0,
    outcome: "OPEN",
    canClose: true,
    canReopen: false,
    canEdit: true,
    canDelete: true,
    createdAt: "x",
    updatedAt: "x",
  };

  function seededClient(item: ActionItem) {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    client.setQueryData(LIST_KEY, { content: [item] });
    return client;
  }

  function clientWrapper(client: QueryClient) {
    return function Wrapper({ children }: { children: ReactNode }) {
      return (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      );
    };
  }

  function cachedItem(client: QueryClient): ActionItem {
    const page = client.getQueryData<{ content: ActionItem[] }>(LIST_KEY);
    return page!.content[0]!;
  }

  function settle(client: QueryClient) {
    return renderHook(() => useSettleActionItem("backend-guild", 5), {
      wrapper: clientWrapper(client),
    });
  }

  /** One control settles an item three ways, so one hook reaches three different endpoints: a
   *  settle that routed the wrong outcome would record the wrong answer with the right gesture. */
  it.each([
    ["DONE" as const, "closeActionItem" as const, "OPEN" as const],
    ["DROPPED" as const, "dropActionItem" as const, "DONE" as const],
    ["OPEN" as const, "reopenActionItem" as const, "DROPPED" as const],
  ])(
    "settles to %s through %s and moves the cached item there first",
    async (outcome, call, from) => {
      gateway[call].mockResolvedValue({} as never);
      const client = seededClient({ ...sample, outcome: from });
      const { result } = settle(client);

      result.current.mutate(outcome);

      await waitFor(() => expect(cachedItem(client).outcome).toBe(outcome));
      expect(gateway[call].mock.calls[0]).toEqual([5]);
    },
  );

  /** Settling is meant to be corrected by pressing again, so a second press routinely lands while
   *  the first is still on the wire. react-query detaches the first press's own callbacks when
   *  that happens, which is why the failure is announced from the mutation rather than the press. */
  it("says a settle failed even when the next press already replaced it", async () => {
    gateway.closeActionItem.mockRejectedValue(new Error("boom"));
    gateway.dropActionItem.mockResolvedValue({} as never);
    const client = seededClient(sample);
    const { result } = settle(client);

    result.current.mutate("DONE");
    result.current.mutate("DROPPED");

    await waitFor(() => expect(notifyError).toHaveBeenCalled());
  });

  /** The server refuses an edit to a settled item, so the row must stop offering one the moment it
   *  is struck through rather than at the next refetch. */
  it("withdraws the right to edit as soon as the item settles", async () => {
    gateway.closeActionItem.mockResolvedValue({} as never);
    const client = seededClient({ ...sample, canEdit: true });
    const { result } = settle(client);

    result.current.mutate("DONE");

    await waitFor(() => expect(cachedItem(client).canEdit).toBe(false));
  });

  it("rolls the optimistic outcome back to the snapshot when the request fails", async () => {
    gateway.closeActionItem.mockRejectedValue(new Error("boom"));
    const client = seededClient(sample);
    const { result } = settle(client);

    result.current.mutate("DONE");

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedItem(client).outcome).toBe("OPEN");
  });
});

describe("nextOutcome", () => {
  it("cycles open, done, dropped, open", () => {
    expect(nextOutcome("OPEN")).toBe("DONE");
    expect(nextOutcome("DONE")).toBe("DROPPED");
    expect(nextOutcome("DROPPED")).toBe("OPEN");
  });
});
