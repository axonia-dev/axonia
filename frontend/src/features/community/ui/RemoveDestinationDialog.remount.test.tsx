import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import type { ChatDestination } from "@/features/community/domain/chat-destination";

import { RemoveDestinationDialog } from "./RemoveDestinationDialog";

/**
 * Unlike `RemoveDestinationDialog.test.tsx`, this file leaves the real
 * `useRemoveChatDestination` hook in place and only mocks the gateway
 * underneath it. The bug this pins - a failed mutation's `isError` surviving
 * into the next destination's confirmation - is a property of a real
 * `useMutation`'s state persisting across re-renders of the same component
 * instance; a fully mocked hook returns whatever a test tells it to on every
 * call and cannot reproduce that persistence, so it cannot tell a fixed
 * `key`-based remount apart from a bug that removes it.
 */
vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    removeChatDestination: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

const DESTINATION_A: ChatDestination = {
  id: 7,
  name: "#guild",
  platform: "SLACK",
  maskedUrl: "hooks.slack.com/...abcd",
  broken: false,
  lastPostedAt: null,
  lastFailureAt: null,
  lastError: null,
};

const DESTINATION_B: ChatDestination = {
  ...DESTINATION_A,
  id: 8,
  name: "#other-team",
};

function wrapper({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("RemoveDestinationDialog remounting", () => {
  it("does not carry a failed attempt's error into the confirmation for a different destination", async () => {
    gateway.removeChatDestination.mockRejectedValueOnce(new Error("boom"));

    const { rerender } = render(
      <RemoveDestinationDialog
        slug="backend-guild"
        destination={DESTINATION_A}
        isOpen
        resetKey={1}
        onOpenChange={vi.fn()}
      />,
      { wrapper },
    );

    await user.click(screen.getByRole("button", { name: "Remove" }));
    expect(await screen.findByRole("alert")).toBeVisible();

    rerender(
      <RemoveDestinationDialog
        slug="backend-guild"
        destination={DESTINATION_B}
        isOpen
        resetKey={2}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole("alert")).toBeNull();
  });
});
