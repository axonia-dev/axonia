import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { saveKeys } from "@/features/bookmarks/api/query-keys";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  scheduleKeys,
  sessionKeys,
  topicKeys,
} from "@/features/events/api/query-keys";
import { useRenameSession } from "@/features/events/api/use-sessions";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { renameSession: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

beforeEach(() => vi.clearAllMocks());

describe("useRenameSession", () => {
  function renderRename() {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRenameSession("backend-guild", 4), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });
    return { client, invalidate, result };
  }

  it("renames the session and seeds its page with the answer", async () => {
    // given
    const renamed = aScheduledSession({
      id: 4,
      title: "S26E10 - DevTool",
      displayTitle: "S26E10 - DevTool",
    });
    gateway.renameSession.mockResolvedValue(renamed);
    const { client, result } = renderRename();

    // when
    result.current.mutate("S26E10 - DevTool");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.renameSession).toHaveBeenCalledWith(4, "S26E10 - DevTool");
    expect(client.getQueryData(sessionKeys.detail(4))).toEqual(renamed);
  });

  it("refreshes every surface that names the session", async () => {
    // given
    gateway.renameSession.mockResolvedValue(aScheduledSession({ id: 4 }));
    const { invalidate, result } = renderRename();

    // when
    result.current.mutate(null);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: scheduleKeys.all });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: sessionKeys.community("backend-guild"),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: sessionKeys.detail(4),
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: saveKeys.mine() });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: topicKeys.community("backend-guild"),
    });
  });
});
