import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { ImpactHeader } from "@/features/recognition/ui/ImpactHeader";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
const mockUseMe = vi.mocked(useMe);

function contribution(
  over: Partial<RecognizedContribution>,
): RecognizedContribution {
  return {
    contributionType: "SLOT_PRESENTED",
    contributionId: 1,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: "A talk",
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount: 1,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseMe.mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant", department: "Platform" },
  } as never);
});

describe("ImpactHeader", () => {
  it("shows the eyebrow, name, role and standing sentence", () => {
    render(<ImpactHeader items={[contribution({})]} />);
    expect(screen.getByText("My impact")).toBeInTheDocument();
    expect(screen.getByText("Gabriel Dinant")).toBeInTheDocument();
    expect(screen.getByText("Platform")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Contributed 1 talk, appreciated by peers in one community.",
      ),
    ).toBeInTheDocument();
  });

  it("renders the contribution-mix legend", () => {
    render(
      <ImpactHeader
        items={[
          contribution({ contributionType: "SLOT_PRESENTED" }),
          contribution({ contributionType: "ARTIFACT_CAPTURED" }),
        ]}
      />,
    );
    expect(screen.getByText("1 talk")).toBeInTheDocument();
    expect(screen.getByText("1 artifact")).toBeInTheDocument();
  });
});
