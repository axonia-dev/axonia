import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import type { CurrentOrganization } from "@/features/admin/domain/current-organization";
import { CommunityNewPage } from "@/pages/CommunityNewPage";

vi.mock("@/features/community/ui/CommunityForm", () => ({
  CommunityForm: () => <div>community form</div>,
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
const mockCurrentOrg = vi.mocked(useCurrentOrganization);

function renderPage() {
  return render(
    <MemoryRouter>
      <CommunityNewPage />
    </MemoryRouter>,
  );
}

function queryResult(data: CurrentOrganization | undefined) {
  return { data } as ReturnType<typeof useCurrentOrganization>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CommunityNewPage", () => {
  it("names the organization the community will be created in", () => {
    // given
    mockCurrentOrg.mockReturnValue(
      queryResult({ id: "acme", name: "Acme", kind: "ENTERPRISE" }),
    );

    // when
    renderPage();

    // then
    expect(screen.getByText(/creating in/i)).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
  });

  it("omits the context line while the current organization is unknown", () => {
    // given
    mockCurrentOrg.mockReturnValue(queryResult(undefined));

    // when
    renderPage();

    // then
    expect(screen.queryByText(/creating in/i)).not.toBeInTheDocument();
  });
});
