import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMyReactionProposals,
  useProposeReaction,
  useReact,
  useReactionDefinitions,
  useReactionEligibility,
  useReactions,
  useRemoveReaction,
  useUnreact,
} from "@/features/recognition/api/use-reactions";
import {
  useLove,
  useLoveSummary,
  useUnlove,
} from "@/features/recognition/api/use-recognition";
import { ContributionActions } from "@/features/recognition/ui/ContributionActions";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useLoveSummary: vi.fn(),
  useLove: vi.fn(),
  useUnlove: vi.fn(),
}));
vi.mock("@/features/recognition/api/use-reactions", () => ({
  useReactions: vi.fn(),
  useReact: vi.fn(),
  useUnreact: vi.fn(),
  useReactionDefinitions: vi.fn(),
  useReactionEligibility: vi.fn(),
  useProposeReaction: vi.fn(),
  useMyReactionProposals: vi.fn(),
  useRemoveReaction: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/auth/current-user", () => ({
  currentUserId: () => "dev-user",
}));

const CONTRIBUTION = {
  contributionType: "SLOT_PRESENTED",
  contributionId: 9,
} as const;

const mutation = { mutate: vi.fn(), isPending: false, isError: false };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useLove).mockReturnValue(mutation as never);
  vi.mocked(useUnlove).mockReturnValue(mutation as never);
  vi.mocked(useLoveSummary).mockReturnValue({
    data: { count: 2, viewerHasLoved: false },
  } as never);
  vi.mocked(useReactions).mockReturnValue({ data: [] } as never);
  vi.mocked(useReact).mockReturnValue(mutation as never);
  vi.mocked(useUnreact).mockReturnValue(mutation as never);
  vi.mocked(useReactionDefinitions).mockReturnValue({ data: [] } as never);
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: { mayPropose: false },
  } as never);
  vi.mocked(useProposeReaction).mockReturnValue(mutation as never);
  vi.mocked(useMyReactionProposals).mockReturnValue({ data: [] } as never);
  vi.mocked(useRemoveReaction).mockReturnValue(mutation as never);
});

/** The nearest common ancestor of the two controls. One row means the wrapper the caller
 *  places is that ancestor; the stacking regression this guards against is exactly the case
 *  where love and the chips end up in two different boxes one above the other. */
function rowOf(love: HTMLElement, react: HTMLElement): HTMLElement {
  let candidate = love.parentElement;
  while (candidate !== null && !candidate.contains(react)) {
    candidate = candidate.parentElement;
  }
  return candidate as HTMLElement;
}

describe("ContributionActions", () => {
  it("draws love and the reaction strip inside one row rather than stacking them", () => {
    const { container } = render(
      <ContributionActions contribution={CONTRIBUTION} />,
    );

    const row = rowOf(
      screen.getByRole("button", { name: /love/i }),
      screen.getByRole("button", { name: /^react$/i }),
    );
    expect(row).toBe(container.firstElementChild);
    expect(row).toHaveClass("flex", "items-center");
  });

  it("keeps love in the corner, with the strip in front of it", () => {
    const { container } = render(
      <ContributionActions contribution={CONTRIBUTION} />,
    );

    // Love last and the strip before it: the strip is the half that grows a chip at a time, so
    // it has to be the half that wraps. Love wrapping took the divider off the row with it.
    expect(container.firstElementChild?.lastElementChild).toContainElement(
      screen.getByRole("button", { name: /love/i }),
    );
  });

  it("leaves the strip last when love draws nothing at all, so no hairline stands alone", () => {
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);

    const { container } = render(
      <ContributionActions
        contribution={CONTRIBUTION}
        contributorUserIds={["dev-user"]}
      />,
    );

    // The divider is a `:not(:last-child)` rule, so a sole contributor with no loves yet has
    // to leave the strip genuinely last, or a hairline is drawn against nothing.
    expect(screen.queryByRole("button", { name: /love/i })).toBeNull();
    expect(container.firstElementChild?.lastElementChild).toContainElement(
      screen.getByRole("button", { name: /^react$/i }),
    );
  });

  it("still offers love on a slot the viewer only co-presents", () => {
    render(
      <ContributionActions
        contribution={CONTRIBUTION}
        contributorUserIds={["dev-user", "someone-else"]}
      />,
    );

    expect(screen.getByRole("button", { name: /love/i })).toBeInTheDocument();
  });
});
