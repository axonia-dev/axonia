import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { topicKeys } from "@/features/events/api/query-keys";
import {
  useCancelSession,
  useCloseSession,
  useRecordFollowUp,
} from "@/features/events/api/use-session";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    cancelSession: vi.fn(),
    closeSession: vi.fn(),
    recordFollowUp: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

beforeEach(() => {
  vi.clearAllMocks();
  gateway.cancelSession.mockResolvedValue({ id: 7 } as never);
  gateway.closeSession.mockResolvedValue({ id: 7 } as never);
  gateway.recordFollowUp.mockResolvedValue({ id: 7 } as never);
});

/** Renders a settling mutation and reports which query keys its success invalidated. */
async function invalidatedBy(
  settle: () => { fire: () => void; done: boolean },
) {
  const client = new QueryClient();
  const invalidate = vi.spyOn(client, "invalidateQueries");
  const { result } = renderHook(settle, {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  });
  result.current.fire();
  await waitFor(() => expect(result.current.done).toBe(true));
  return invalidate.mock.calls.map(([filters]) => filters?.queryKey);
}

/**
 * Settling a session's fate rewrites its topics: cancelling, or closing it as not held, returns
 * them to the backlog with the slots' presenters as their speakers, and a follow-up marks them
 * done. The Backlog, Scheduled and Past tabs read none of that from the session's own payload.
 */
describe("settling a session refreshes the community's topics", () => {
  it("on cancel", async () => {
    const keys = await invalidatedBy(() => {
      const cancel = useCancelSession("guild");
      return { fire: () => cancel.mutate(7), done: cancel.isSuccess };
    });

    expect(keys).toContainEqual(topicKeys.community("guild"));
  });

  it("on closing as not held", async () => {
    const keys = await invalidatedBy(() => {
      const close = useCloseSession("guild");
      return {
        fire: () => close.mutate({ sessionId: 7, input: { held: false } }),
        done: close.isSuccess,
      };
    });

    expect(keys).toContainEqual(topicKeys.community("guild"));
  });

  it("on recording the follow-up", async () => {
    const keys = await invalidatedBy(() => {
      const followUp = useRecordFollowUp("guild", 7);
      return {
        fire: () =>
          followUp.mutate({ notes: "", recordingUrl: "", decisions: [] }),
        done: followUp.isSuccess,
      };
    });

    expect(keys).toContainEqual(topicKeys.community("guild"));
  });
});
