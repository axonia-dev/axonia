import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { OrganizationGroupAccessRoute } from "@/routes/settings-routes.organization-gates";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/ui/GroupAccessPage", () => ({
  GroupAccessPage: () => <p>Group access page</p>,
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);

function organization(kind: "ENTERPRISE" | "HUB" | undefined) {
  mockUseCurrentOrganization.mockReturnValue({
    data: kind === undefined ? undefined : { id: "org-1", name: "Org", kind },
    isError: false,
  } as never);
}

// Rendered under a route table rather than bare, so a `Navigate` lands somewhere observable
// instead of throwing: the settings base is the destination the gate redirects to.
function renderGate() {
  return render(
    <MemoryRouter initialEntries={["/settings/organization/group-access"]}>
      <Routes>
        <Route
          path="/settings/organization/group-access"
          element={<OrganizationGroupAccessRoute base="" />}
        />
        <Route
          path="/settings/organization"
          element={<p>Organization settings overview</p>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  organization("ENTERPRISE");
});

describe("OrganizationGroupAccessRoute", () => {
  it("serves the page to an organization that has a directory", () => {
    renderGate();

    expect(screen.getByText("Group access page")).toBeInTheDocument();
  });

  it("redirects a hub, which brokers no directory a group could come from", () => {
    organization("HUB");

    renderGate();

    expect(screen.queryByText("Group access page")).not.toBeInTheDocument();
    expect(
      screen.getByText("Organization settings overview"),
    ).toBeInTheDocument();
  });

  it("waits rather than reading an unresolved kind as no directory", () => {
    organization(undefined);

    renderGate();

    expect(screen.queryByText("Group access page")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Organization settings overview"),
    ).not.toBeInTheDocument();
  });
});
