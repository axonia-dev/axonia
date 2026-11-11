import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import { useUnhideTag } from "@/features/expertise/api/use-expertise-prefs";
import type { SelfDeclaration } from "@/features/expertise/domain/expertise";
import { DeclaredLane } from "@/features/expertise/ui/DeclaredLane";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useUnhideTag: vi.fn(),
}));
vi.mock("@/features/expertise/ui/AddDeclarationField", () => ({
  AddDeclarationField: () => <p>add field</p>,
}));
vi.mock("@/features/expertise/ui/DeclarationPreview", () => ({
  DeclarationPreview: ({
    tags,
    paused,
  }: {
    tags: string[];
    paused: boolean;
  }) => (
    <p>
      preview of {tags.join("+")}
      {paused ? " paused" : ""}
    </p>
  ),
}));
vi.mock("@/features/expertise/ui/TakingQuestionsSwitch", () => ({
  TakingQuestionsSwitch: ({
    paused,
    inCommunity,
  }: {
    paused: boolean;
    inCommunity: boolean;
  }) => (
    <p>
      switch {paused ? "off" : "on"}
      {inCommunity ? " in a community" : ""}
    </p>
  ),
}));

const TERRAFORM: SelfDeclaration = {
  tag: "terraform",
  communityId: 1,
  communityName: "Platform",
  communitySlug: "platform",
  hidden: false,
  declaredAt: "2026-09-12T12:00:00Z",
};

const PLATFORM = { id: 1, slug: "platform", name: "Platform" };

function renderLane(
  preview: {
    tags: string[];
    tier: "OPEN_TO_QUESTIONS" | null;
    paused: boolean;
  },
  { across = false }: { across?: boolean } = {},
) {
  return render(
    <DeclaredLane
      declarations={[TERRAFORM]}
      slug={across ? undefined : "platform"}
      communityId={1}
      declarableCommunities={[PLATFORM]}
      preview={preview}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useRemoveDeclaration).mockReturnValue({
    mutate: vi.fn(),
    isRemoving: () => false,
  } as never);
  vi.mocked(useUnhideTag).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useIsDelegated).mockReturnValue(false);
});

describe("DeclaredLane preview", () => {
  it("laneShowsPreviewAndSwitch", () => {
    // when
    renderLane({
      tags: ["terraform", "kafka"],
      tier: "OPEN_TO_QUESTIONS",
      paused: true,
    });

    // then
    expect(screen.getByText("add field")).toBeInTheDocument();
    expect(screen.getByText("switch off in a community")).toBeInTheDocument();
    expect(
      screen.getByText("preview of terraform+kafka paused"),
    ).toBeInTheDocument();
  });

  it("delegatedSessionShowsNeither", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);

    // when
    renderLane({
      tags: ["terraform"],
      tier: "OPEN_TO_QUESTIONS",
      paused: false,
    });

    // then
    expect(screen.queryByText(/^preview of/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^switch/)).not.toBeInTheDocument();
    expect(screen.queryByText("add field")).not.toBeInTheDocument();
    expect(screen.getByText("terraform")).toBeInTheDocument();
  });

  it("hidesThePreviewWithNothingDeclared", () => {
    // when
    renderLane({ tags: [], tier: null, paused: false });

    // then
    expect(screen.queryByText(/^preview of/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^switch/)).not.toBeInTheDocument();
  });

  it("acrossCommunitiesTheSwitchCarriesNoCommunityNote", () => {
    // when
    renderLane(
      { tags: ["terraform"], tier: "OPEN_TO_QUESTIONS", paused: false },
      { across: true },
    );

    // then
    expect(screen.getByText("switch on")).toBeInTheDocument();
  });

  it("setsThePreviewBesideTheFieldOnlyWhenThePanelItselfIsWide", () => {
    // when
    renderLane({
      tags: ["terraform"],
      tier: "OPEN_TO_QUESTIONS",
      paused: false,
    });

    // then
    const grid = screen.getByText("preview of terraform").parentElement!;
    expect(grid.className).toMatch(/(^| )@2xl:grid-cols-2( |$)/);
    expect(grid.className).not.toMatch(/(^| )sm:grid-cols-2( |$)/);
    expect(grid.parentElement!.className).toMatch(/(^| )@container( |$)/);
  });
});
