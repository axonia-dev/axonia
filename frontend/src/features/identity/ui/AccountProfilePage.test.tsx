import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { activeOrganizationId } from "@/auth/active-organization";
import { useAuth } from "@/auth/session";
import { getAccessToken } from "@/auth/token";
import { useAccessMe } from "@/features/admin/api/use-access-me";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import { useMe } from "@/features/identity/api/use-me";
import type { User } from "@/features/identity/domain/user";
import { AccountProfilePage } from "@/features/identity/ui/AccountProfilePage";

vi.mock("@/auth/session", () => ({ useAuth: vi.fn() }));
vi.mock("@/auth/token", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: vi.fn(),
}));
vi.mock("@/features/admin/api/use-access-me", () => ({
  useAccessMe: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-self-view", () => ({
  useSelfView: vi.fn(),
}));
vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, activeOrganizationId: vi.fn() };
});

const mockUseAuth = vi.mocked(useAuth);
const mockGetAccessToken = vi.mocked(getAccessToken);
const mockUseMyOrganizations = vi.mocked(useMyOrganizations);
const mockUseAccessMe = vi.mocked(useAccessMe);
const mockUseSelfView = vi.mocked(useSelfView);
const mockActiveOrganizationId = vi.mocked(activeOrganizationId);
const mockUseMe = vi.mocked(useMe);

const ME: User = {
  id: "sub-1",
  displayName: "Alice Anderson",
  email: "alice@example.com",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
};

function withProviders({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

function renderPage() {
  return render(<AccountProfilePage />, { wrapper: withProviders });
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseAuth.mockReturnValue({
    user: { profile: { sub: "sub-1" } },
    signinRedirect: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>);
  mockGetAccessToken.mockReturnValue("seam-token");
  mockActiveOrganizationId.mockReturnValue(undefined);
  mockUseMe.mockReturnValue({ data: ME } as unknown as ReturnType<
    typeof useMe
  >);
  mockUseMyOrganizations.mockReturnValue({
    data: [],
  } as unknown as ReturnType<typeof useMyOrganizations>);
  mockUseAccessMe.mockReturnValue({
    data: undefined,
  } as unknown as ReturnType<typeof useAccessMe>);
  mockUseSelfView.mockReturnValue({
    data: undefined,
  } as unknown as ReturnType<typeof useSelfView>);
});

describe("AccountProfilePage", () => {
  it("lists no organizations where the edition supplies no picker", () => {
    mockActiveOrganizationId.mockReturnValue("default");
    mockUseMyOrganizations.mockReturnValue({
      data: [
        { id: "default", name: "Axonia" },
        { id: "acme", name: "Acme" },
      ],
    } as unknown as ReturnType<typeof useMyOrganizations>);

    renderPage();

    expect(
      screen.getByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Organizations")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Switch" })).toBeNull();
  });

  it("carries no explanatory prose", () => {
    renderPage();

    expect(
      screen.queryByText(/login provider asserts/i),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/refresh each time you sign in/i),
    ).not.toBeInTheDocument();
  });

  it("opens the picture dialog from the hero", async () => {
    renderPage();

    await user.click(screen.getByRole("button", { name: "Change picture" }));

    expect(
      screen.getByRole("dialog", { name: "Profile picture" }),
    ).toBeInTheDocument();
  });
});
