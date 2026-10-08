import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { SessionToVerify } from "@/features/events/ui/SessionToVerify";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    listActionItems: vi.fn(),
    closeActionItem: vi.fn(),
    reopenActionItem: vi.fn(),
    dropActionItem: vi.fn(),
  },
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const gateway = vi.mocked(httpEventsGateway);
const mockUseUsers = vi.mocked(useUsers);

function item(overrides: Partial<ActionItem> = {}): ActionItem {
  return {
    id: 1,
    communityId: 1,
    title: "Renew the certificate",
    description: "",
    tags: [],
    raisedByUserId: "alice",
    raisedByDisplayName: "Alice",
    assigneeUserId: null,
    assigneeDisplayName: null,
    dueDate: null,
    originSessionId: 7,
    checkpoint: {
      sessionId: 42,
      scheduledAt: null,
      pinned: true,
      governingTimeZone: null,
    },
    carryCount: 0,
    outcome: "OPEN",
    canClose: false,
    canReopen: false,
    canEdit: false,
    canDelete: false,
    createdAt: "x",
    updatedAt: "x",
    ...overrides,
  };
}

function pageOf(items: ActionItem[]) {
  return {
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 50,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseUsers.mockReturnValue({ data: {} } as never);
});

interface RenderOptions {
  items?: ActionItem[];
  fails?: boolean;
}

/**
 * The empty arm renders nothing, so no element in the DOM marks the moment the fetch answered
 * and a synchronous assertion lands on the pending arm instead. This shares the component's own
 * query, so it appears exactly when that query settles.
 */
function SettledProbe() {
  const { isPending } = useActionItems("backend-guild", {
    checkpointSessionId: 42,
    page: 0,
    size: 50,
  });
  return isPending ? null : <span data-testid="settled" />;
}

function renderToVerify({
  items = [],
  fails = false,
}: RenderOptions): ReactElement {
  if (fails) {
    gateway.listActionItems.mockRejectedValue(new Error("boom"));
  } else {
    gateway.listActionItems.mockResolvedValue(pageOf(items));
  }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <SessionToVerify slug="backend-guild" sessionId={42} />
      <SettledProbe />
    </QueryClientProvider>
  );
}

describe("SessionToVerify", () => {
  it("lists what this session owes an answer on", async () => {
    render(
      renderToVerify({
        items: [item({ title: "Renew the certificate", carryCount: 0 })],
      }),
    );

    expect(
      await screen.findByText("Renew the certificate"),
    ).toBeInTheDocument();
  });

  it("renders an error rather than the empty state when the fetch fails", async () => {
    render(renderToVerify({ fails: true }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load",
    );
    expect(screen.queryByText(/Nothing/)).not.toBeInTheDocument();
  });

  it("renders nothing at all when the session owes nothing", async () => {
    render(renderToVerify({ items: [] }));

    await screen.findByTestId("settled");
    expect(
      screen.queryByRole("heading", { name: /To verify/i }),
    ).not.toBeInTheDocument();
  });

  it("asks the gateway for this session's checkpoint, not its origin", async () => {
    render(renderToVerify({ items: [item()] }));

    await screen.findByText("Renew the certificate");
    expect(gateway.listActionItems.mock.calls[0]).toEqual([
      "backend-guild",
      { checkpointSessionId: 42, page: 0, size: 50 },
    ]);
  });

  /** Settling an item from this list must not make it vanish: without the row there is no undo,
   *  and finding the item again means hunting for the session that raised it. */
  it("keeps an item it has already settled, below the open ones", async () => {
    render(
      renderToVerify({
        // Settled first, as the server's own order can hand them over: the section has to lift
        // the open ones above them rather than rely on the page arriving sorted.
        items: [
          item({
            id: 2,
            title: "Already dropped",
            outcome: "DROPPED",
            canReopen: true,
          }),
          item({ id: 1, title: "Still open", outcome: "OPEN" }),
        ],
      }),
    );

    await screen.findByText("Still open");
    const titles = screen
      .getAllByRole("listitem")
      .map((row) => row.textContent);
    expect(titles[0]).toContain("Still open");
    expect(titles[1]).toContain("Already dropped");
  });

  it("counts only what is still outstanding", async () => {
    render(
      renderToVerify({
        items: [
          item({ id: 1, title: "Still open", outcome: "OPEN" }),
          item({
            id: 2,
            title: "Done here",
            outcome: "DONE",
            canReopen: true,
          }),
        ],
      }),
    );

    await screen.findByText("Still open");
    expect(
      screen.getByRole("heading", { name: /To verify/ }),
    ).toHaveTextContent("1");
  });

  /** Every item here answers at the session being read, so a chip saying so on each row repeats
   *  the page's own title and links to the page you are on. */
  it("states no checkpoint on a row, because the page is the checkpoint", async () => {
    render(
      renderToVerify({
        items: [item({ id: 1, title: "Still open", outcome: "OPEN" })],
      }),
    );

    await screen.findByText("Still open");
    expect(screen.queryByText(/Next session/)).not.toBeInTheDocument();
  });
});
