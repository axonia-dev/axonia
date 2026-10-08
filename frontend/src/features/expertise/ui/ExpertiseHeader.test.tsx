import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  SelfArea,
  SelfDeclaration,
  Tier,
} from "@/features/expertise/domain/expertise";
import { ExpertiseHeader } from "@/features/expertise/ui/ExpertiseHeader";
import { useMe } from "@/features/identity/api/use-me";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));

const mockUseMe = vi.mocked(useMe);

function area(tier: Tier): SelfArea {
  return {
    canonicalTag: tier,
    tier,
    hidden: false,
    communities: [],
    evidence: {
      presentedCount: 1,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
  };
}

function declaration(tag: string, communityId: number): SelfDeclaration {
  return {
    tag,
    communityId,
    communityName: `Community ${communityId}`,
    communitySlug: `community-${communityId}`,
    hidden: false,
    declaredAt: "2026-09-12T12:00:00Z",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseMe.mockReturnValue({
    data: {
      id: "u1",
      displayName: "Gabriel Dinant",
      department: "Platform Engineering",
    },
  } as never);
});

describe("ExpertiseHeader", () => {
  it("shows the member identity and standing sentence", () => {
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [area("PRACTITIONER")],
          declarations: [],
          declarableCommunities: [],
        }}
      />,
    );

    expect(screen.getByText("Gabriel Dinant")).toBeInTheDocument();
    expect(screen.getByText("Platform Engineering")).toBeInTheDocument();
    expect(screen.getByText("Practitioner in 1 area.")).toBeInTheDocument();
  });

  it("renders a tier distribution legend and signal total", () => {
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [area("PRACTITIONER"), area("CONTRIBUTOR")],
          declarations: [],
          declarableCommunities: [],
        }}
      />,
    );

    expect(screen.getByText("1 Practitioner")).toBeInTheDocument();
    expect(screen.getByText("1 Contributor")).toBeInTheDocument();
    expect(screen.getByText("2 signals")).toBeInTheDocument();
  });

  it("names a single signal in the singular", () => {
    // when
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [area("PRACTITIONER")],
          declarations: [],
          declarableCommunities: [],
        }}
      />,
    );

    // then
    expect(screen.getByText("1 signal")).toBeInTheDocument();
  });

  it("leaves the signal total out when only declarations are counted", () => {
    // when
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [],
          declarations: [declaration("kafka", 1)],
          declarableCommunities: [],
        }}
      />,
    );

    // then
    expect(screen.queryByText(/signal/)).not.toBeInTheDocument();
  });

  it("names the member's team before the department, as practitioner cards do", () => {
    // given
    mockUseMe.mockReturnValue({
      data: {
        id: "u1",
        displayName: "Bob Brandt",
        department: "Platform",
        team: "Build & CI",
      },
    } as never);

    // when
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [],
          declarations: [],
          declarableCommunities: [],
        }}
      />,
    );

    // then
    expect(screen.getByText("Build & CI")).toBeInTheDocument();
    expect(screen.queryByText("Platform")).not.toBeInTheDocument();
  });

  it("renders the action slot", () => {
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [],
          declarations: [],
          declarableCommunities: [],
        }}
        action={<button type="button">Visibility</button>}
      />,
    );

    expect(
      screen.getByRole("button", { name: "Visibility" }),
    ).toBeInTheDocument();
  });

  it("counts every community's declared-only areas in the distribution", () => {
    // when
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [],
          declarations: [declaration("kafka", 1), declaration("spark", 2)],
          declarableCommunities: [],
        }}
      />,
    );

    // then
    expect(
      screen.getByText("Open to questions in 2 areas."),
    ).toBeInTheDocument();
    expect(screen.getByText("2 Open to questions")).toBeInTheDocument();
  });

  it("leaves out a declaration the evidence of its community already shows", () => {
    // when
    render(
      <ExpertiseHeader
        view={{
          optedOut: false,
          areas: [
            {
              ...area("PRACTITIONER"),
              communities: [{ id: 1, slug: "p", name: "P" }],
            },
          ],
          declarations: [declaration("PRACTITIONER", 1)],
          declarableCommunities: [],
        }}
      />,
    );

    // then
    expect(screen.queryByText(/Open to questions/)).not.toBeInTheDocument();
  });
});
