import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../adapters/http-agent-connection-gateway", () => ({
  httpAgentConnectionGateway: {
    listMine: vi.fn(() => Promise.resolve([])),
    disconnect: vi.fn(() => Promise.resolve()),
    review: vi.fn(() =>
      Promise.resolve({
        outcome: "READY",
        clientName: "Claude Code",
        clientHost: "claude.ai",
        requestedScopes: ["read"],
      }),
    ),
    approve: vi.fn(() =>
      Promise.resolve({
        outcome: "REDIRECT",
        redirectUrl: "http://localhost/cb",
      }),
    ),
    deny: vi.fn(() =>
      Promise.resolve({
        outcome: "REDIRECT",
        redirectUrl: "http://localhost/cb",
      }),
    ),
  },
}));

import { httpAgentConnectionGateway } from "../adapters/http-agent-connection-gateway";
import { agentKeys } from "./query-keys";
import {
  useAuthorizationReview,
  useDisconnectConnection,
} from "./use-agent-connections";

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return { client, Wrapper };
}

describe("useAuthorizationReview", () => {
  it("reviews the request once", async () => {
    const { Wrapper } = makeWrapper();

    const { result } = renderHook(
      () => useAuthorizationReview({ client_id: "x" }),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.data?.outcome).toBe("READY"));
    expect(httpAgentConnectionGateway.review).toHaveBeenCalledTimes(1);
  });
});

describe("useDisconnectConnection", () => {
  it("refreshes the connection list", async () => {
    const { client, Wrapper } = makeWrapper();
    const invalidate = vi.spyOn(client, "invalidateQueries");

    const { result } = renderHook(() => useDisconnectConnection(), {
      wrapper: Wrapper,
    });
    result.current.mutate(4);

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: agentKeys.connections(),
      }),
    );
  });
});
