import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import type { ActionItem } from "@/features/events/domain/action-item";
import { SessionActionPoints } from "@/features/events/ui/SessionActionPoints";
import { useUsers } from "@/features/identity/api/use-users";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listActionItems: vi.fn(),
    raiseActionItem: vi.fn(),
    closeActionItem: vi.fn(),
    reopenActionItem: vi.fn(),
    dropActionItem: vi.fn(),
  },
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const gateway = vi.mocked(httpEventsGateway);
const mockUseUsers = vi.mocked(useUsers);

const OPEN_ITEM = anActionItem();

function pageOf(items: ActionItem[]) {
  return {
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 50,
  };
}

let currentItems: ActionItem[] = [];

function renderComponent(): ReactElement {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <SessionActionPoints slug="backend-guild" sessionId={42} />
    </QueryClientProvider>
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  currentItems = [];
  gateway.listActionItems.mockImplementation(() =>
    Promise.resolve(pageOf(currentItems)),
  );
  mockUseUsers.mockReturnValue({ data: {} } as never);
});

/** The settle control for one action point, once the list has loaded. */
function settleButtonFor(item: ActionItem, status: string) {
  return screen.findByRole("button", { name: `${status}: ${item.title}` });
}

describe("SessionActionPoints", () => {
  it("filters the community's action items to this session's origin", async () => {
    render(renderComponent());
    await waitFor(() => expect(gateway.listActionItems).toHaveBeenCalled());
    expect(gateway.listActionItems.mock.calls[0]).toEqual([
      "backend-guild",
      { originSessionId: 42, page: 0, size: 50 },
    ]);
  });

  it("renders the capture trigger as the entry point when nothing was captured yet", async () => {
    render(renderComponent());
    await screen.findByText(/nothing was captured from this session yet/i);
    expect(
      screen.getByRole("button", { name: /\+ action point/i }),
    ).toBeInTheDocument();
  });

  it("reports a failed fetch instead of claiming there is nothing to see", async () => {
    gateway.listActionItems.mockRejectedValue(new Error("boom"));
    render(renderComponent());

    expect(
      await screen.findByText(/could not load action points/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/nothing was captured from this session yet/i),
    ).not.toBeInTheDocument();
  });

  it("closes the action point immediately when its own control is pressed", async () => {
    currentItems = [OPEN_ITEM];
    let resolveClose: (item: ActionItem) => void = () => {};
    gateway.closeActionItem.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveClose = resolve;
        }),
    );
    render(renderComponent());
    const button = await settleButtonFor(OPEN_ITEM, "Not done");

    await user.click(button);

    // optimistic: the strikethrough lands before the server responds
    await waitFor(() =>
      expect(screen.getByText(OPEN_ITEM.title)).toHaveClass("line-through"),
    );

    const closedItem: ActionItem = {
      ...OPEN_ITEM,
      outcome: "DONE",
      canClose: false,
      canReopen: true,
    };
    currentItems = [closedItem];
    resolveClose(closedItem);

    await waitFor(() =>
      expect(gateway.closeActionItem).toHaveBeenCalledWith(OPEN_ITEM.id),
    );
  });

  it("rolls the control back when closing fails", async () => {
    currentItems = [OPEN_ITEM];
    let rejectClose: (error: Error) => void = () => {};
    gateway.closeActionItem.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectClose = reject;
        }),
    );
    render(renderComponent());
    const button = await settleButtonFor(OPEN_ITEM, "Not done");

    // The failed mutation's onSettled still invalidates and refetches listActionItems.
    // Pointing that refetch at a promise that never resolves means the assertion
    // below can only pass because onError restored the snapshot - a refetch that
    // happened to return the same stale data could never fake it.
    gateway.listActionItems.mockImplementation(() => new Promise(() => {}));

    await user.click(button);

    // optimistic: struck through before the server responds
    await waitFor(() =>
      expect(screen.getByText(OPEN_ITEM.title)).toHaveClass("line-through"),
    );

    // then the server refuses, and the row snaps back to the last known-good state
    rejectClose(new Error("boom"));
    await waitFor(() =>
      expect(screen.getByText(OPEN_ITEM.title)).not.toHaveClass("line-through"),
    );
  });

  it("notifies when closing fails", async () => {
    currentItems = [OPEN_ITEM];
    gateway.closeActionItem.mockRejectedValue(new Error("boom"));
    render(renderComponent());
    const button = await settleButtonFor(OPEN_ITEM, "Not done");

    await user.click(button);

    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith(
        "Could not update that action point. Try again.",
      ),
    );
  });

  it("drops a done row when its own control is pressed again", async () => {
    const doneItem: ActionItem = {
      ...OPEN_ITEM,
      outcome: "DONE",
      canClose: false,
      canReopen: true,
    };
    currentItems = [doneItem];
    let resolveDrop: (item: ActionItem) => void = () => {};
    gateway.dropActionItem.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveDrop = resolve;
        }),
    );
    render(renderComponent());
    const button = await settleButtonFor(doneItem, "Done");

    await user.click(button);

    await waitFor(async () =>
      expect(await settleButtonFor(doneItem, "Won’t do")).toBeInTheDocument(),
    );

    const dropped: ActionItem = {
      ...doneItem,
      outcome: "DROPPED",
      canClose: false,
      canReopen: true,
    };
    currentItems = [dropped];
    resolveDrop(dropped);

    await waitFor(() =>
      expect(gateway.dropActionItem).toHaveBeenCalledWith(doneItem.id),
    );
  });

  it("shows a static indicator, not a control, for a member without permission", async () => {
    const noPermissionItem: ActionItem = {
      ...OPEN_ITEM,
      title: "Try the new consumer config on staging",
      canClose: false,
      canReopen: false,
    };
    currentItems = [noPermissionItem];
    render(renderComponent());
    await screen.findByText(noPermissionItem.title);

    expect(
      screen.queryByRole("button", { name: /^(Not done|Done|Won’t do): / }),
    ).not.toBeInTheDocument();
    expect(gateway.closeActionItem).not.toHaveBeenCalled();
    expect(gateway.reopenActionItem).not.toHaveBeenCalled();
  });

  it("tells the member when the list was truncated, rather than silently dropping the rest", async () => {
    gateway.listActionItems.mockResolvedValue({
      content: [OPEN_ITEM],
      totalElements: 63,
      totalPages: 2,
      number: 0,
      size: 50,
    });
    render(renderComponent());
    await screen.findByText(OPEN_ITEM.title);

    expect(
      screen.getByText(/showing the first 1 of 63 action points/i),
    ).toBeInTheDocument();
    // the badge reports the real total, not just what fetched
    expect(screen.getByText("63")).toBeInTheDocument();
  });

  it("shows no truncation note when every action point fetched", async () => {
    currentItems = [OPEN_ITEM];
    render(renderComponent());
    await screen.findByText(OPEN_ITEM.title);

    expect(screen.queryByText(/showing the first/i)).not.toBeInTheDocument();
  });

  function settled(
    id: number,
    title: string,
    outcome: ActionItem["outcome"],
  ): ActionItem {
    return {
      ...OPEN_ITEM,
      id,
      title,
      outcome,
      canClose: false,
      canReopen: outcome === "DONE",
    };
  }

  it("keeps a done row visible, struck through and muted, distinct from an open row", async () => {
    const closedItem = settled(404, "Share the whiteboard photos", "DONE");
    currentItems = [OPEN_ITEM, closedItem];
    render(renderComponent());
    await screen.findByText(OPEN_ITEM.title);

    expect(screen.getByText(OPEN_ITEM.title)).not.toHaveClass("line-through");
    expect(screen.getByText(closedItem.title)).toHaveClass("line-through");
  });

  it("keeps a dropped row visible, struck through and muted, with its own label", async () => {
    const droppedItem = settled(405, "Migrate the old dashboards", "DROPPED");
    currentItems = [droppedItem];
    render(renderComponent());
    await screen.findByText(droppedItem.title);

    expect(screen.getByText(droppedItem.title)).toHaveClass("line-through");
    expect(screen.getByText("Won’t do")).toBeInTheDocument();
    expect(screen.queryByText("Done")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });
});
