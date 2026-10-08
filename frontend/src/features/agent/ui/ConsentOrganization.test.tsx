import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockOrganizations = vi.fn();
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => ({ data: mockOrganizations() as unknown }),
}));
const mockActiveId = vi.fn<() => string | undefined>();
vi.mock("@/auth/active-organization", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  activeOrganizationId: () => mockActiveId(),
}));

import { ConsentOrganization } from "./ConsentOrganization";

function renderRow() {
  return render(
    <MemoryRouter initialEntries={["/oauth/authorize?client_id=x"]}>
      <ConsentOrganization />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockActiveId.mockReturnValue("default");
  mockOrganizations.mockReturnValue([
    { id: "default", name: "Axonia" },
    { id: "acme", name: "Acme" },
  ]);
});

describe("ConsentOrganization", () => {
  it("names the session's organization and offers no other where the edition supplies no picker", () => {
    renderRow();

    expect(screen.getByText("Axonia")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows the organization alone when the token already holds the only one", () => {
    mockOrganizations.mockReturnValue([{ id: "default", name: "Axonia" }]);

    renderRow();

    expect(screen.getByText("Axonia")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("says nothing while the memberships load", () => {
    mockOrganizations.mockReturnValue(undefined);

    const { container } = renderRow();

    expect(container).toBeEmptyDOMElement();
  });

  it("says so when the caller belongs to no organization", () => {
    mockOrganizations.mockReturnValue([]);

    renderRow();

    expect(screen.getByText("No organization")).toBeInTheDocument();
  });
});
