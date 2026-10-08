import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { activeOrganizationId } from "@/auth/active-organization";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { OrganizationSwitcher } from "@/features/admin/ui/OrganizationSwitcher";

vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: vi.fn(),
}));
vi.mock("@/auth/token", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, activeOrganizationId: vi.fn() };
});

const mockUseMyOrganizations = vi.mocked(useMyOrganizations);
const mockActiveOrganizationId = vi.mocked(activeOrganizationId);

function makeWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}
const wrapper = makeWrapper(new QueryClient());

function mockOrgs(
  orgs: ReadonlyArray<{
    id: string;
    name: string;
    badgeUrl?: string | null;
    accentColor?: string | null;
  }>,
) {
  mockUseMyOrganizations.mockReturnValue({ data: orgs } as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockActiveOrganizationId.mockReturnValue(undefined);
});

// Switching moved off this chip entirely, to the tenant settings page; this chip is
// identity only now, for every organization count.
describe("OrganizationSwitcher", () => {
  it("renders nothing while membership is loading", () => {
    mockUseMyOrganizations.mockReturnValue({ data: undefined } as never);
    const { container } = render(<OrganizationSwitcher />, { wrapper });
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when the caller belongs to no organization", () => {
    mockOrgs([]);
    const { container } = render(<OrganizationSwitcher />, { wrapper });
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a static badge for a single org with no switch control", () => {
    mockOrgs([{ id: "acme", name: "Acme" }]);
    render(<OrganizationSwitcher />, { wrapper });
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows identity only, with no switch control, even with several organizations", () => {
    mockActiveOrganizationId.mockReturnValue("acme");
    mockOrgs([
      { id: "acme", name: "Acme" },
      { id: "globex", name: "Globex" },
    ]);
    render(<OrganizationSwitcher />, { wrapper });
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByText("Globex")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("full variant shows the org name without the redundant eyebrow", () => {
    mockOrgs([{ id: "acme", name: "Acme" }]);
    render(<OrganizationSwitcher variant="full" />, { wrapper });
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByText("Organization")).not.toBeInTheDocument();
  });

  it("wears the Axonia mark tinted by the org, with the name as the label", () => {
    mockOrgs([{ id: "acme", name: "Acme" }]);
    render(<OrganizationSwitcher variant="full" />, { wrapper });
    // The brand mark is the avatar — no separate logo tile, no initials badge.
    expect(screen.getByRole("img", { name: /axonia/i })).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
  });

  it("tints the trigger face's mark with the org's accent color when it has one", () => {
    mockOrgs([{ id: "acme", name: "Acme", accentColor: "#ff6600" }]);
    render(<OrganizationSwitcher variant="full" />, { wrapper });
    const mark = screen.getByRole("img", { name: /axonia/i });
    expect(mark.parentElement).toHaveStyle({ "--logo-tint": "#ff6600" });
  });

  it("falls back to the first org as active when the token carries no org claim", () => {
    // given — multi-org caller whose access token has no `org` claim yet
    mockActiveOrganizationId.mockReturnValue(undefined);
    mockOrgs([
      { id: "acme", name: "Acme" },
      { id: "globex", name: "Globex" },
    ]);

    // when
    render(<OrganizationSwitcher />, { wrapper });

    // then — Acme (the first org) is shown as the identity
    expect(screen.getByText("Acme")).toBeInTheDocument();
  });

  it("compact variant renders a bare avatar with no label and no switch control", () => {
    mockActiveOrganizationId.mockReturnValue("acme");
    mockOrgs([
      { id: "acme", name: "Acme" },
      { id: "globex", name: "Globex" },
    ]);
    render(<OrganizationSwitcher variant="compact" />, { wrapper });
    expect(screen.queryByText("Acme")).not.toBeInTheDocument();
    expect(screen.getByTitle("Acme")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  describe("the name's pop", () => {
    const POP = "motion-safe:animate-[count-pop_0.3s_ease]";

    it("does not play when the face appears", () => {
      // given
      mockOrgs([{ id: "acme", name: "Acme" }]);

      // when
      render(<OrganizationSwitcher variant="full" />, { wrapper });

      // then
      expect(screen.getByText("Acme")).not.toHaveClass(POP);
    });

    it("plays when the organization changes in place", () => {
      // given
      mockActiveOrganizationId.mockReturnValue("acme");
      mockOrgs([
        { id: "acme", name: "Acme" },
        { id: "globex", name: "Globex" },
      ]);
      const { rerender } = render(<OrganizationSwitcher variant="full" />, {
        wrapper,
      });

      // when
      mockActiveOrganizationId.mockReturnValue("globex");
      rerender(<OrganizationSwitcher variant="full" />);

      // then
      expect(screen.getByText("Globex")).toHaveClass(POP);
    });
  });
});
