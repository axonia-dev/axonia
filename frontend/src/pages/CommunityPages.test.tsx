import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import { useCommunity } from "@/features/community/api/use-communities";
import { CommunitiesPage } from "@/pages/CommunitiesPage";
import { CommunityEditPage } from "@/pages/CommunityEditPage";
import { CommunityNewPage } from "@/pages/CommunityNewPage";
import { permissionState } from "@/test/permissions";
import { ThemeProvider } from "@/theme/ThemeProvider";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/community/ui/CommunityList", () => ({
  CommunityList: () => <div>community-list</div>,
}));
vi.mock("@/features/community/ui/CommunityForm", () => ({
  CommunityForm: ({
    community,
    onCreated,
  }: {
    community?: { name: string };
    onCreated: (slug: string) => void;
  }) => (
    <div>
      form:{community?.name ?? "new"}
      <button type="button" onClick={() => onCreated("saved-slug")}>
        save
      </button>
    </div>
  ),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(() => ({
    isLoading: false,
    data: { slug: "backend-guild", name: "Backend Guild" },
  })),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({
    data: { id: "acme", name: "Acme", kind: "ENTERPRISE" },
  }),
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUsePermissions = vi.mocked(usePermissions);

describe("community pages", () => {
  beforeEach(() => {
    mockUsePermissions.mockReturnValue(permissionState(() => true));
  });

  it("CommunitiesPage renders the directory", () => {
    // given / when
    render(
      <ThemeProvider>
        <MemoryRouter>
          <CommunitiesPage />
        </MemoryRouter>
      </ThemeProvider>,
    );

    // then
    expect(screen.getByText("community-list")).toBeInTheDocument();
  });

  it("CommunitiesPage hides the create CTA without community:create", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(
      <ThemeProvider>
        <MemoryRouter>
          <CommunitiesPage />
        </MemoryRouter>
      </ThemeProvider>,
    );

    // then
    expect(
      screen.queryByRole("link", { name: "Start a community" }),
    ).not.toBeInTheDocument();
  });

  it("CommunitiesPage shows the create CTA with community:create", () => {
    // given
    mockUsePermissions.mockReturnValue(
      permissionState(
        (permission: string) => permission === "community:create",
      ),
    );

    // when
    render(
      <ThemeProvider>
        <MemoryRouter>
          <CommunitiesPage />
        </MemoryRouter>
      </ThemeProvider>,
    );

    // then
    expect(
      screen.getByRole("link", { name: "Start a community" }),
    ).toHaveAttribute("href", "/communities/new");
  });

  it("CommunityNewPage renders the create form", () => {
    // given / when
    render(
      <MemoryRouter>
        <CommunityNewPage />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText("form:new")).toBeInTheDocument();
  });

  it("CommunityNewPage navigates to the created community on success", async () => {
    // given
    render(
      <MemoryRouter initialEntries={["/communities/new"]}>
        <Routes>
          <Route path="/communities/new" element={<CommunityNewPage />} />
          <Route
            path="/communities/:slug"
            element={<div>landed:saved-slug</div>}
          />
        </Routes>
      </MemoryRouter>,
    );

    // when
    await user.click(screen.getByRole("button", { name: "save" }));

    // then
    expect(screen.getByText("landed:saved-slug")).toBeInTheDocument();
  });

  it("CommunityEditPage renders the form pre-filled", () => {
    // given / when
    render(
      <MemoryRouter initialEntries={["/communities/backend-guild/edit"]}>
        <Routes>
          <Route
            path="/communities/:slug/edit"
            element={<CommunityEditPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText("form:Backend Guild")).toBeInTheDocument();
  });

  it("CommunityEditPage navigates to the saved community on success", async () => {
    // given
    render(
      <MemoryRouter initialEntries={["/communities/backend-guild/edit"]}>
        <Routes>
          <Route
            path="/communities/:slug/edit"
            element={<CommunityEditPage />}
          />
          <Route
            path="/communities/:slug"
            element={<div>landed:saved-slug</div>}
          />
        </Routes>
      </MemoryRouter>,
    );

    // when
    await user.click(screen.getByRole("button", { name: "save" }));

    // then
    expect(screen.getByText("landed:saved-slug")).toBeInTheDocument();
  });

  it("CommunityEditPage shows a loading state while fetching", () => {
    // given
    vi.mocked(useCommunity).mockReturnValueOnce({
      isLoading: true,
      data: undefined,
    } as never);

    // when
    render(
      <MemoryRouter initialEntries={["/communities/backend-guild/edit"]}>
        <Routes>
          <Route
            path="/communities/:slug/edit"
            element={<CommunityEditPage />}
          />
        </Routes>
      </MemoryRouter>,
    );

    // then
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
