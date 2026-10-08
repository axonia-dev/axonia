import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
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
import type { ReactionTally } from "@/features/recognition/domain/reaction";
import { ReactionBar } from "@/features/recognition/ui/ReactionBar";

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
  useCurrentOrganization: vi.fn(),
}));

const CONTRIBUTION = {
  contributionType: "SLOT_PRESENTED",
  contributionId: 1,
} as const;

/** The server names a definition, and a standard emoji has none: `name` is null on every
 *  EMOJI tally the backend builds, so the fixture carries that null rather than a word. */
const BRAIN = "🧠";
/** The count joins the accessible name, since `aria-label` hides whatever the chip draws. */
const BRAIN_CHIP = `${BRAIN}, 3`;

function emojiTally(emoji: string, count = 1): ReactionTally {
  return {
    kind: "EMOJI",
    emoji,
    definitionId: null,
    name: null,
    imageUrl: null,
    count,
    viewerHasReacted: false,
  };
}

/** Distinct characters, taken in order from the emoticon block. */
function manyTallies(howMany: number): ReactionTally[] {
  return Array.from({ length: howMany }, (_, i) =>
    emojiTally(String.fromCodePoint(0x1f600 + i)),
  );
}

const TALLIES: ReactionTally[] = [
  {
    kind: "EMOJI",
    emoji: BRAIN,
    definitionId: null,
    name: null,
    imageUrl: null,
    count: 3,
    viewerHasReacted: false,
  },
  {
    kind: "CUSTOM",
    emoji: null,
    definitionId: 1,
    name: "ship-it",
    imageUrl: "https://cdn.example/ship-it.png",
    count: 2,
    viewerHasReacted: true,
  },
];

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useReactions).mockReturnValue({ data: TALLIES } as never);
  vi.mocked(useReact).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useUnreact).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useReactionDefinitions).mockReturnValue({ data: [] } as never);
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: {
      mayPropose: false,
      openProposals: 0,
      openProposalLimit: 3,
      approvedLimit: 60,
    },
  } as never);
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: undefined,
  } as never);
  vi.mocked(useProposeReaction).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useMyReactionProposals).mockReturnValue({ data: [] } as never);
  vi.mocked(useRemoveReaction).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

describe("ReactionBar", () => {
  it("draws a chip per reaction, with its count", () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    expect(
      screen.getByRole("button", { name: BRAIN_CHIP }),
    ).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("names a standard emoji chip by its character, the only word the server sends", () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    const chip = screen.getByRole("button", { name: BRAIN_CHIP });
    expect(chip.parentElement).toHaveAttribute("title", BRAIN);
  });

  it("names a custom reaction in the accessible label rather than on the chip face", () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    const chip = screen.getByRole("button", { name: /ship-it/i });
    expect(chip).toHaveTextContent("2");
    expect(chip).not.toHaveTextContent("ship-it");
  });

  it("marks the viewer's own with aria-pressed", () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    expect(screen.getByRole("button", { name: /ship-it/i })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("does not mark a reaction the viewer has not placed", () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    expect(screen.getByRole("button", { name: BRAIN_CHIP })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("opens the picker from the add control", async () => {
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: /^react$/i }));

    expect(await screen.findByRole("searchbox")).toBeInTheDocument();
  });

  it("renders only the add control when nothing has been placed", () => {
    vi.mocked(useReactions).mockReturnValue({ data: [] } as never);

    render(<ReactionBar contribution={CONTRIBUTION} />);

    expect(
      screen.getByRole("button", { name: /^react$/i }),
    ).toBeInTheDocument();
    expect(screen.queryAllByRole("button")).toHaveLength(1);
  });

  it("names the trigger for the act, leaving the words 'add a reaction' to the one entry point that proposes one", async () => {
    vi.mocked(useReactionEligibility).mockReturnValue({
      data: {
        mayPropose: true,
        openProposals: 0,
        openProposalLimit: 3,
        approvedLimit: 60,
      },
    } as never);
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: /^react$/i }));

    expect(
      screen.getAllByRole("button", { name: /^add a reaction$/i }),
    ).toHaveLength(1);
  });

  it("withdraws an already-placed reaction on a second press", async () => {
    const mutate = vi.fn();
    vi.mocked(useUnreact).mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: /ship-it/i }));

    expect(mutate).toHaveBeenCalledWith({ kind: "CUSTOM", definitionId: 1 });
  });

  it("places a reaction not yet placed by the viewer", async () => {
    const mutate = vi.fn();
    vi.mocked(useReact).mockReturnValue({ mutate, isPending: false } as never);
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: BRAIN_CHIP }));

    expect(mutate).toHaveBeenCalledWith({ kind: "EMOJI", emoji: BRAIN });
  });

  it("opens the propose dialog and closes the picker popover first, so the two never stack", async () => {
    vi.mocked(useReactionEligibility).mockReturnValue({
      data: {
        mayPropose: true,
        openProposals: 0,
        openProposalLimit: 3,
        approvedLimit: 60,
      },
    } as never);
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: /^react$/i }));
    await user.click(screen.getByRole("button", { name: /^add a reaction$/i }));

    expect(
      screen.getByRole("dialog", { name: /add a reaction/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
  });

  it("leaves the chip bare when one person placed it, so a row of ones says nothing twelve times", () => {
    vi.mocked(useReactions).mockReturnValue({
      data: [emojiTally("🔥")],
    } as never);

    render(<ReactionBar contribution={CONTRIBUTION} />);

    const chip = screen.getByRole("button", { name: "🔥" });
    expect(chip).not.toHaveTextContent("1");
  });

  it("collapses the reactions past the fourth behind one control", () => {
    vi.mocked(useReactions).mockReturnValue({
      data: manyTallies(12),
    } as never);

    render(<ReactionBar contribution={CONTRIBUTION} />);

    expect(
      screen.getByRole("button", { name: "8 more reactions" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "😋" })).toBeNull();
  });

  it("still toggles a reaction reached through the overflow", async () => {
    const mutate = vi.fn();
    vi.mocked(useReact).mockReturnValue({ mutate, isPending: false } as never);
    vi.mocked(useReactions).mockReturnValue({
      data: manyTallies(12),
    } as never);
    render(<ReactionBar contribution={CONTRIBUTION} />);

    await user.click(screen.getByRole("button", { name: "8 more reactions" }));
    await user.click(await screen.findByRole("button", { name: "😋" }));

    expect(mutate).toHaveBeenCalledWith({ kind: "EMOJI", emoji: "😋" });
  });
});
