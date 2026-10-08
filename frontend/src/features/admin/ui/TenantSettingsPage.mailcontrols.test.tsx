import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { TenantSettingsPage } from "@/features/admin/ui/TenantSettingsPage";

// Split out of TenantSettingsPage.test.tsx (300-line ceiling). Stubs the two cards
// themselves - a visible marker each - rather than the permission/settings/mutation
// hooks each one reads: the point here is only to prove the page no longer renders
// either card at all, the same way OrganizationNotificationsPage.test.tsx proves the
// opposite for its own page with the identical stub. Their real behaviour has its own
// coverage in CommunicationsKillSwitch.test.tsx and RoundupCard.test.tsx.
vi.mock("@/auth/session", () => ({
  useAuth: () => ({ signinRedirect: vi.fn() }),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: vi.fn(() => ({ data: [{ id: "acme", name: "Acme" }] })),
}));
vi.mock("@/features/admin/ui/CommunicationsKillSwitch", () => ({
  CommunicationsKillSwitch: () => <p>Kill switch</p>,
}));
vi.mock("@/features/admin/ui/RoundupCard", () => ({
  RoundupCard: () => <p>Roundup card</p>,
}));
vi.mock("@/auth/token", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, activeOrganizationId: vi.fn() };
});

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route path="/" element={<TenantSettingsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("TenantSettingsPage mail controls", () => {
  it("leaves the organization's mail controls to the notifications section", () => {
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: { id: "acme", name: "Acme", kind: "ENTERPRISE" },
    } as never);

    renderPage();

    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByText("Kill switch")).toBeNull();
    expect(screen.queryByText("Roundup card")).toBeNull();
  });
});
