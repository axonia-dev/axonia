import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { vi } from "vitest";

import { useActionItems } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import { ActionItemsTab } from "@/features/events/ui/ActionItemsTab";
import { anActionItem } from "@/test/action-item-fixture";

let nextId = 1;

/** Ids only have to be distinct within one test, so each one restarts the sequence. */
export function resetActionItemIds() {
  nextId = 1;
}

export function anOpenItem(
  overrides: Partial<Omit<ActionItem, "checkpoint">> & {
    checkpoint?: Partial<ActionItem["checkpoint"]>;
  } = {},
): ActionItem {
  const { checkpoint, ...rest } = overrides;
  return anActionItem({
    id: nextId++,
    originSessionId: null,
    assigneeUserId: null,
    assigneeDisplayName: null,
    canClose: false,
    checkpoint: {
      sessionId: null,
      scheduledAt: null,
      pinned: false,
      governingTimeZone: null,
      ...checkpoint,
    },
    ...rest,
  });
}

/** What the tab's one read is doing by the time the tab renders. */
type ReadState = "answered" | "loading" | "failed";

interface RenderOptions {
  items?: ActionItem[];
  total?: number;
  state?: ReadState;
}

function pageOf(items: ActionItem[], total: number) {
  return {
    content: items,
    totalElements: total,
    totalPages: 1,
    number: 0,
    size: 50,
  };
}

/** Callers own the `vi.mock` of the hook - vitest hoists it per file. */
export function renderActionItemsTab({
  items = [],
  total = items.length,
  state = "answered",
}: Readonly<RenderOptions> = {}) {
  vi.mocked(useActionItems).mockReturnValue({
    data: state === "answered" ? pageOf(items, total) : undefined,
    isLoading: state === "loading",
    isError: state === "failed",
  } as never);
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ActionItemsTab slug="backend-guild" />
    </QueryClientProvider>,
  );
}
