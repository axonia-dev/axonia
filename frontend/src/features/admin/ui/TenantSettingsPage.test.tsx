import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { TenantSettingsPage } from "@/features/admin/ui/TenantSettingsPage";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: vi.fn(),
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);
const mockUseMyOrganizations = vi.mocked(useMyOrganizations);

function mockOrg(
  kind: "ENTERPRISE" | "HUB",
  id = "acme",
  name = "Acme",
  memberCount?: number,
) {
  mockUseCurrentOrganization.mockReturnValue({
    data: { id, name, kind, memberCount },
  } as never);
}

function mockOrgs(
  orgs: ReadonlyArray<{
    id: string;
    name: string;
    badgeUrl?: string | null;
    accentColor?: string | null;
    kind?: "HUB" | "ENTERPRISE" | null;
  }>,
) {
  mockUseMyOrganizations.mockReturnValue({ data: orgs } as never);
}

function renderPage() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<TenantSettingsPage />} />
          <Route path="/discover" element={<p>discover page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("TenantSettingsPage hero", () => {
  it("renders nothing while the current organization or membership is loading", () => {
    mockUseCurrentOrganization.mockReturnValue({ data: undefined } as never);
    mockOrgs([]);
    const { container } = renderPage();
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the current organization's name and badge", () => {
    mockOrg("ENTERPRISE", "acme", "Acme");
    mockOrgs([{ id: "acme", name: "Acme", badgeUrl: "https://cdn/acme.png" }]);
    renderPage();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Acme" })).toHaveAttribute(
      "src",
      "https://cdn/acme.png",
    );
  });

  it("labels the kind of tenant Organization for an enterprise", () => {
    mockOrg("ENTERPRISE", "acme", "Acme");
    mockOrgs([{ id: "acme", name: "Acme" }]);
    renderPage();
    expect(screen.getAllByText("Organization").length).toBeGreaterThan(0);
  });

  it("labels the kind of tenant Community for a hub", () => {
    mockOrg("HUB", "acme", "Acme");
    mockOrgs([{ id: "acme", name: "Acme" }]);
    renderPage();
    expect(screen.getAllByText("Community").length).toBeGreaterThan(0);
  });

  it("shows the member count, pluralised, when the organization has one", () => {
    mockOrg("ENTERPRISE", "acme", "Acme", 42);
    mockOrgs([{ id: "acme", name: "Acme" }]);
    renderPage();
    expect(screen.getByText("42 members")).toBeInTheDocument();
  });

  it("keeps the member count singular for exactly one member", () => {
    mockOrg("ENTERPRISE", "acme", "Acme", 1);
    mockOrgs([{ id: "acme", name: "Acme" }]);
    renderPage();
    expect(screen.getByText("1 member")).toBeInTheDocument();
  });

  it("renders no member count line when the organization has none", () => {
    mockOrg("ENTERPRISE", "acme", "Acme");
    mockOrgs([{ id: "acme", name: "Acme" }]);
    renderPage();
    expect(screen.queryByText(/member/i)).not.toBeInTheDocument();
  });
});

// The mail-controls-are-gone assertion lives in TenantSettingsPage.mailcontrols.test.tsx:
// it needs its own stubs for the two cards.

describe("TenantSettingsPage where the edition supplies no organization picker", () => {
  it("shows the tenant alone: no other tenant to switch to, nothing to discover or create", () => {
    mockOrg("HUB", "acme", "Acme");
    mockOrgs([
      { id: "acme", name: "Acme", kind: "HUB" },
      { id: "globex", name: "Globex", kind: "ENTERPRISE" },
    ]);
    renderPage();

    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByText("Globex")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /discover communities/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /create a community/i }),
    ).not.toBeInTheDocument();
  });
});
