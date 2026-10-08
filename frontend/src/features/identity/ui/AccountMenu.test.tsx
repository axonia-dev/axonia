import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  setStoredActiveOrganizationId,
  storedActiveOrganizationId,
} from "@/auth/active-organization";
import { DelegatedSessionProvider } from "@/auth/delegated-session";
import { MenuItem } from "@/components/ui/Menu";
import { EditionProvider } from "@/edition-context";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useMe } from "@/features/identity/api/use-me";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import { openSlots } from "@/open-slots";
import { permissionState } from "@/test/permissions";
import { ThemeProvider } from "@/theme/ThemeProvider";

const signoutRedirect = vi.fn();
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUsePermissions = vi.mocked(usePermissions);

const profile = {
  preferred_username: "alice",
  sub: "sub-1",
  email: "alice@example.com",
  picture: undefined as string | undefined,
};

vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile },
    signoutRedirect,
  }),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  signoutRedirect.mockClear();
  mockUsePermissions.mockReturnValue(permissionState(() => false));
});

afterEach(() => {
  profile.picture = undefined;
});

vi.mock("@/features/identity/api/use-me", () => ({
  useMe: vi.fn(() => ({ data: undefined })),
}));

function wrapper({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
}

const alice = {
  id: "sub-1",
  displayName: "Alice Anderson",
  email: "alice@example.com",
};

describe("AccountMenu", () => {
  it("reveals identity only once opened", async () => {
    vi.mocked(useMe).mockReturnValue({ data: alice } as unknown as ReturnType<
      typeof useMe
    >);
    render(<AccountMenu />, { wrapper });
    expect(screen.queryByText("Alice Anderson")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /account/i }));
    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("does not repeat the address when Entra has no display name to fall back from", async () => {
    // A live Entra tenant left this member with no display name mapped, so the
    // fallback chain resolved the same address onto both the name and email.
    vi.mocked(useMe).mockReturnValue({
      data: { id: "sub-1", displayName: "Bob@x.com", email: "bob@x.com" },
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });

    await user.click(screen.getByRole("button", { name: /account/i }));
    expect(screen.getAllByText(/bob@x\.com/i)).toHaveLength(1);
  });

  it("falls back to the token username while /me loads", async () => {
    vi.mocked(useMe).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    await user.click(screen.getByRole("button", { name: /account/i }));
    expect(screen.getByText("alice")).toBeInTheDocument();
  });

  it("logs out via the IdP", async () => {
    vi.mocked(useMe).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    await user.click(screen.getByRole("button", { name: /account/i }));
    await user.click(screen.getByRole("menuitem", { name: /log out/i }));
    expect(signoutRedirect).toHaveBeenCalledOnce();
  });

  it("leaves no trace of the account for whoever signs in next", async () => {
    // given a member standing in a hub
    setStoredActiveOrganizationId("acme");
    vi.mocked(useMe).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    await user.click(screen.getByRole("button", { name: /account/i }));

    // when they log out
    await user.click(screen.getByRole("menuitem", { name: /log out/i }));

    // then the next account signs in on its own membership, not theirs: the stored organization
    // rides the startup OIDC scope into the very next authorize request
    expect(storedActiveOrganizationId()).toBeUndefined();
  });

  it("carries Log out in the danger tone", async () => {
    vi.mocked(useMe).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    await user.click(screen.getByRole("button", { name: /account/i }));
    expect(screen.getByRole("menuitem", { name: /log out/i })).toHaveClass(
      "text-danger",
    );
  });

  it("falls back to the profile email when /me has no email", async () => {
    vi.mocked(useMe).mockReturnValue({
      data: { id: "sub-1", displayName: "Alice Anderson", email: null },
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    await user.click(screen.getByRole("button", { name: /account/i }));
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("shows the profile picture instead of initials when available", () => {
    vi.mocked(useMe).mockReturnValue({
      data: { ...alice, avatarUrl: "https://cdn.example.com/alice.png" },
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    expect(screen.getByRole("img", { name: "Alice Anderson" })).toHaveAttribute(
      "src",
      "https://cdn.example.com/alice.png",
    );
  });

  it("shows initials rather than hotlinking the token picture while /me loads", () => {
    // The OIDC ID token's `picture` claim points at the identity provider's own host,
    // which the content security policy blocks. The account menu must never fall back
    // to it: no avatar from our API means initials, not a third-party image request.
    profile.picture = "https://idp.example.com/alice.png";
    vi.mocked(useMe).mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("shows initials rather than the token picture even when /me has no avatar", () => {
    // Pins a browser finding: an API profile with a null avatar
    // plus an OIDC profile carrying a `picture` claim must never render that claim's
    // URL as an <img src>, since the CSP blocks it and it hotlinks a third party.
    profile.picture = "https://idp.example.com/alice.png";
    vi.mocked(useMe).mockReturnValue({
      data: { ...alice, avatarUrl: null },
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("shows the profile picture in the opened menu header", async () => {
    vi.mocked(useMe).mockReturnValue({
      data: { ...alice, avatarUrl: "https://cdn.example.com/alice.png" },
    } as unknown as ReturnType<typeof useMe>);
    render(<AccountMenu />, { wrapper });

    await user.click(screen.getByRole("button", { name: /account/i }));

    const images = screen.getAllByRole("img", { name: "Alice Anderson" });
    expect(images.length).toBeGreaterThanOrEqual(1);
    for (const img of images) {
      expect(img).toHaveAttribute("src", "https://cdn.example.com/alice.png");
    }
  });
});

describe("AccountMenu in a delegated tab", () => {
  async function openDelegatedMenu(edition = openSlots) {
    vi.mocked(useMe).mockReturnValue({ data: alice } as unknown as ReturnType<
      typeof useMe
    >);
    render(
      <EditionProvider edition={edition}>
        <DelegatedSessionProvider>
          <AccountMenu />
        </DelegatedSessionProvider>
      </EditionProvider>,
      { wrapper },
    );
    await user.click(screen.getByRole("button", { name: /account/i }));
  }

  it("offers no Log out, since the tab has no sign-in of its own to leave", async () => {
    // when
    await openDelegatedMenu();

    // then
    expect(
      screen.queryByRole("menuitem", { name: /log out/i }),
    ).not.toBeInTheDocument();
  });

  it("offers the edition's own way out in Log out's place", async () => {
    // given
    function Exit() {
      return <MenuItem id="exit">Leave</MenuItem>;
    }

    // when
    await openDelegatedMenu({ ...openSlots, DelegatedSessionExit: Exit });

    // then
    expect(screen.getByRole("menuitem", { name: "Leave" })).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: /log out/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps Log out for a tab signed in on its own, whatever the edition supplies", async () => {
    // given
    function Exit() {
      return <MenuItem id="exit">Leave</MenuItem>;
    }
    vi.mocked(useMe).mockReturnValue({ data: alice } as unknown as ReturnType<
      typeof useMe
    >);
    render(
      <EditionProvider edition={{ ...openSlots, DelegatedSessionExit: Exit }}>
        <AccountMenu />
      </EditionProvider>,
      { wrapper },
    );

    // when
    await user.click(screen.getByRole("button", { name: /account/i }));
    await user.click(screen.getByRole("menuitem", { name: /log out/i }));

    // then
    expect(screen.queryByText("Leave")).not.toBeInTheDocument();
    expect(signoutRedirect).toHaveBeenCalledOnce();
  });
});
