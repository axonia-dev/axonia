import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useMe } from "@/features/identity/api/use-me";
import { useMyWords } from "@/features/mentoring/api/use-mentoring";
import { words } from "@/features/mentoring/test-data";
import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import { useMyContributions } from "@/features/recognition/api/use-recognition";
import { MyImpactView } from "@/features/recognition/ui/MyImpactView";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useMyContributions: vi.fn(),
}));
vi.mock("@/features/recognition/api/use-new-loves", () => ({
  useNewLoves: vi.fn(),
  useMarkImpactSeen: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMyWords: vi.fn(),
  useOpenWords: vi.fn(() => ({ mutate: vi.fn() })),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));

const contribution = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 1,
  communityId: 1,
  communitySlug: "eng",
  communityName: "Engineering",
  title: "Resilience talk",
  occurredAt: "2026-06-01T00:00:00Z",
  loveCount: 0,
  sessionId: null,
  typeId: null,
  typeLabel: null,
  typeIcon: null,
  typeAccentColor: null,
};

function feed(wordsList: (typeof words)[]) {
  vi.mocked(useMyWords).mockReturnValue({ data: wordsList } as never);
  vi.mocked(useMyContributions).mockReturnValue({
    data: [contribution],
    isLoading: false,
    isError: false,
  } as never);
}

function view() {
  render(
    <MemoryRouter>
      <MyImpactView />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
  vi.mocked(useNewLoves).mockReturnValue({ data: undefined } as never);
  vi.mocked(useMarkImpactSeen).mockReturnValue({ mutate: vi.fn() } as never);
  vi.mocked(useCommunity).mockReturnValue({ data: undefined } as never);
});

describe("MyImpactView words from mentees", () => {
  it("shows the words after the contributions", () => {
    // given
    feed([words]);

    // when
    view();

    // then
    const contributions = screen.getByRole("link", { name: "Resilience talk" });
    const section = screen.getByRole("heading", { name: "Words from mentees" });
    expect(
      contributions.compareDocumentPosition(section) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Open the words from Noah" }),
    ).toBeInTheDocument();
  });

  it("takes no part in the new loves replay", () => {
    // given
    feed([]);
    view();
    const without = vi.mocked(useNewLoves).mock.calls.at(-1);
    vi.clearAllMocks();
    feed([words]);

    // when
    view();

    // then
    expect(vi.mocked(useNewLoves).mock.calls.at(-1)).toEqual(without);
  });
});
