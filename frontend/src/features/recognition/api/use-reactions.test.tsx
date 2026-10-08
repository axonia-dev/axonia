import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpReactionGateway } from "@/features/recognition/adapters/http-reaction-gateway";
import {
  useApproveReaction,
  useMyReactionProposals,
  usePendingReactionCount,
  useReactionEligibility,
  useReactions,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";

vi.mock("@/features/recognition/adapters/http-reaction-gateway", () => ({
  httpReactionGateway: {
    tally: vi.fn(),
    react: vi.fn(),
    unreact: vi.fn(),
    listDefinitions: vi.fn(),
    listAllDefinitions: vi.fn(),
    listMyProposals: vi.fn(),
    eligibility: vi.fn(),
    propose: vi.fn(),
    approve: vi.fn(),
    remove: vi.fn(),
  },
}));

const gateway = vi.mocked(httpReactionGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const KEY = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 9,
};

function definition(
  overrides: Partial<ReactionDefinition> = {},
): ReactionDefinition {
  return {
    id: 1,
    name: "ship-it",
    imageUrl: "https://cdn/ship.png",
    pending: false,
    proposedByUserId: null,
    usageCount: 0,
    ...overrides,
  };
}

/** A bar already on screen, with its first tally answered. */
async function aMountedBar() {
  gateway.tally.mockResolvedValue([]);
  const { result } = renderHook(
    () => ({
      tallies: useReactions(KEY),
      approve: useApproveReaction(),
      remove: useRemoveReaction(),
    }),
    { wrapper },
  );
  await waitFor(() => expect(result.current.tallies.isSuccess).toBe(true));
  expect(gateway.tally).toHaveBeenCalledTimes(1);
  return result;
}

beforeEach(() => vi.clearAllMocks());

describe("a catalog change", () => {
  it("refetches a mounted bar's tallies when a definition is removed", async () => {
    // given a removal cascades its placements away in the database
    gateway.remove.mockResolvedValue(undefined);
    const result = await aMountedBar();

    // when
    result.current.remove.mutate(1);

    // then the bar asks again rather than keeping a chip for a reaction that is gone
    await waitFor(() => expect(gateway.tally).toHaveBeenCalledTimes(2));
  });

  it("refetches a mounted bar's tallies when a definition is approved", async () => {
    // given
    gateway.approve.mockResolvedValue(definition());
    const result = await aMountedBar();

    // when
    result.current.approve.mutate(1);

    // then
    await waitFor(() => expect(gateway.tally).toHaveBeenCalledTimes(2));
  });
});

describe("withdrawing a proposal", () => {
  /** The cap message has to stop being true the moment a slot frees, so the count and the list
   *  both go stale on the same withdrawal. */
  it("refetches the proposal list and the eligibility count together", async () => {
    // given
    gateway.listMyProposals.mockResolvedValue([
      definition({ id: 1, pending: true }),
    ]);
    gateway.eligibility.mockResolvedValue({
      mayPropose: true,
      openProposals: 1,
      openProposalLimit: 3,
      approvedLimit: 60,
    });
    gateway.remove.mockResolvedValue(undefined);
    const { result } = renderHook(
      () => ({
        proposals: useMyReactionProposals(),
        eligibility: useReactionEligibility(),
        withdraw: useRemoveReaction(),
      }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.proposals.isSuccess).toBe(true));
    await waitFor(() =>
      expect(result.current.eligibility.isSuccess).toBe(true),
    );
    expect(gateway.listMyProposals).toHaveBeenCalledTimes(1);
    expect(gateway.eligibility).toHaveBeenCalledTimes(1);

    // when
    result.current.withdraw.mutate(1);

    // then
    await waitFor(() =>
      expect(gateway.listMyProposals).toHaveBeenCalledTimes(2),
    );
    await waitFor(() => expect(gateway.eligibility).toHaveBeenCalledTimes(2));
  });
});

describe("usePendingReactionCount", () => {
  it("counts only the proposals still waiting", async () => {
    // given
    gateway.listAllDefinitions.mockResolvedValue([
      definition({ id: 1, pending: true }),
      definition({ id: 2, pending: true }),
      definition({ id: 3, pending: false }),
    ]);

    // when
    const { result } = renderHook(() => usePendingReactionCount(true), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current).toBe(2));
  });

  it("asks for nothing on behalf of a caller who may not read the queue", () => {
    // when
    const { result } = renderHook(() => usePendingReactionCount(false), {
      wrapper,
    });

    // then
    expect(result.current).toBe(0);
    expect(gateway.listAllDefinitions).not.toHaveBeenCalled();
  });
});
