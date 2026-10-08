import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { useSelfView } from "@/features/expertise/api/use-self-view";
import type {
  SelfArea,
  SelfDeclaration,
  SelfView,
  Tier,
} from "@/features/expertise/domain/expertise";
import { AccountExpertise } from "@/features/identity/ui/AccountExpertise";

vi.mock("@/features/expertise/api/use-self-view", () => ({
  useSelfView: vi.fn(),
}));

const mockUseSelfView = vi.mocked(useSelfView);

function withQueryClient({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

function area(canonicalTag: string, tier: Tier): SelfArea {
  return {
    canonicalTag,
    tier,
    hidden: false,
    communities: [],
    evidence: {
      presentedCount: 0,
      contributedCount: 0,
      offeredCount: 0,
      recent: [],
    },
    communityTiers: [],
  };
}

function stubSelfView(
  view: Omit<SelfView, "declarations" | "declarableCommunities">,
  declarations: SelfDeclaration[] = [],
) {
  mockUseSelfView.mockReturnValue({
    data: { ...view, declarations, declarableCommunities: [] },
  } as unknown as ReturnType<typeof useSelfView>);
}

describe("AccountExpertise", () => {
  it("shows each area with a tier its tooltip names", () => {
    stubSelfView({
      optedOut: false,
      areas: [area("kafka", "PRACTITIONER"), area("rust", "INTERESTED")],
    });

    render(<AccountExpertise />, { wrapper: withQueryClient });

    expect(screen.getByText("kafka")).toBeInTheDocument();
    expect(screen.getByTitle("Practitioner")).toBeInTheDocument();
  });

  it("shows no chip for an area only a declaration carries", () => {
    // given
    const declaration: SelfDeclaration = {
      tag: "terraform",
      communityId: 1,
      communityName: "Platform",
      communitySlug: "platform",
      hidden: false,
      declaredAt: "2026-09-12T12:00:00Z",
    };
    stubSelfView({ optedOut: false, areas: [area("kafka", "PRACTITIONER")] }, [
      declaration,
    ]);

    // when
    render(<AccountExpertise />, { wrapper: withQueryClient });

    // then
    expect(screen.getByText("kafka")).toBeInTheDocument();
    expect(screen.queryByText("terraform")).not.toBeInTheDocument();
  });

  it("excludes hidden areas", () => {
    stubSelfView({
      optedOut: false,
      areas: [
        area("kafka", "PRACTITIONER"),
        { ...area("secret", "PRACTITIONER"), hidden: true },
      ],
    });

    render(<AccountExpertise />, { wrapper: withQueryClient });

    expect(screen.queryByText("secret")).not.toBeInTheDocument();
  });

  it("renders nothing at all for a member who opted out of the graph", () => {
    stubSelfView({ optedOut: true, areas: [area("kafka", "PRACTITIONER")] });

    const { container } = render(<AccountExpertise />, {
      wrapper: withQueryClient,
    });

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when every area is hidden", () => {
    stubSelfView({
      optedOut: false,
      areas: [
        { ...area("kafka", "PRACTITIONER"), hidden: true },
        { ...area("rust", "INTERESTED"), hidden: true },
      ],
    });

    const { container } = render(<AccountExpertise />, {
      wrapper: withQueryClient,
    });

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing while the query has not resolved", () => {
    mockUseSelfView.mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useSelfView>);

    const { container } = render(<AccountExpertise />, {
      wrapper: withQueryClient,
    });

    expect(container).toBeEmptyDOMElement();
  });

  it("caps the list at 12 chips without announcing a truncation", () => {
    const areas = Array.from({ length: 30 }, (_, i) =>
      area(`tag-${i}`, "PRACTITIONER"),
    );
    stubSelfView({ optedOut: false, areas });

    const { container } = render(<AccountExpertise />, {
      wrapper: withQueryClient,
    });

    expect(screen.getAllByTitle("Practitioner")).toHaveLength(12);
    expect(screen.queryByText(/more/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/showing/i)).not.toBeInTheDocument();
    expect(container.textContent).not.toContain("12 of 30");
  });
});
