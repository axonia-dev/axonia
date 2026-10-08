import { screen } from "@testing-library/react";
import type userEvent from "@testing-library/user-event";
import { vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import { useUsers } from "@/features/identity/api/use-users";
import {
  useAllReactionDefinitions,
  useApproveReaction,
  useReactionEligibility,
  useRemoveReaction,
} from "@/features/recognition/api/use-reactions";
import type { ReactionDefinition } from "@/features/recognition/domain/reaction";
import { permissionState } from "@/test/permissions";

/** What the backend actually stores: the Keycloak subject, which reads as nothing to a human. */
export const PROPOSER_ID = "3f9c1b42-7a1d-4e55-9a4b-1c2d3e4f5a6b";

export const pending: ReactionDefinition = {
  id: 1,
  name: "yak-shaving",
  imageUrl: "https://cdn/yak.png",
  pending: true,
  proposedByUserId: PROPOSER_ID,
  usageCount: 0,
};

const approved: ReactionDefinition = {
  id: 2,
  name: "ship-it",
  imageUrl: "https://cdn/ship.png",
  pending: false,
  proposedByUserId: null,
  usageCount: 48,
};

export function allow(...permissions: string[]) {
  vi.mocked(usePermissions).mockReturnValue(
    permissionState((permission: string) => permissions.includes(permission)),
  );
}

export function givenDefinitions(...all: ReactionDefinition[]) {
  vi.mocked(useAllReactionDefinitions).mockReturnValue({ data: all } as never);
}

/** `useApproveReaction`'s shape; a caller spells out only what a scenario needs to differ. */
export function approveReactionResult(
  overrides: Partial<{
    mutate: ReturnType<typeof vi.fn>;
    error: unknown;
    reset: ReturnType<typeof vi.fn>;
  }> = {},
) {
  return {
    mutate: vi.fn(),
    isPending: false,
    reset: vi.fn(),
    ...overrides,
  } as never;
}

/** `useRemoveReaction`'s shape; a caller spells out only what a scenario needs to differ. */
export function removeReactionResult(
  overrides: Partial<{
    mutate: ReturnType<typeof vi.fn>;
    isError: boolean;
    error: unknown;
    reset: ReturnType<typeof vi.fn>;
  }> = {},
) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    reset: vi.fn(),
    ...overrides,
  } as never;
}

export async function openShipItRemoval(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.click(screen.getByRole("button", { name: /remove ship-it/i }));
}

/** The state every scenario starts from: one proposal waiting, one reaction already in use. */
export function givenAQueueWithOneProposal() {
  vi.clearAllMocks();
  givenDefinitions(pending, approved);
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: {
      mayPropose: true,
      openProposals: 0,
      openProposalLimit: 3,
      approvedLimit: 60,
    },
  } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: { [PROPOSER_ID]: { id: PROPOSER_ID, displayName: "Mia Nguyen" } },
  } as never);
  vi.mocked(useApproveReaction).mockReturnValue(approveReactionResult());
  vi.mocked(useRemoveReaction).mockReturnValue(removeReactionResult());
  allow("reaction:write");
}
