import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useMe } from "@/features/identity/api/use-me";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of AccountMenu.test.tsx (300-line ceiling), mirroring
// AccountMenu.get-help.test.tsx's harness: the settings entries are their own
// cohesive concern, gated on being scoped into an organization at all (the
// tenant section it opens on is ungated - see AccountMenu's own doc comment)
// rather than on identity or session state.

const signoutRedirect = vi.fn();

vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
    signoutRedirect,
  }),
}));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));

vi.mock("@/features/identity/api/use-me", () => ({
  useMe: vi.fn(() => ({ data: undefined })),
}));

const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);

const alice = {
  id: "sub-1",
  displayName: "Alice Anderson",
  email: "alice@example.com",
};

function wrapper({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
}

function renderMenu() {
  vi.mocked(useMe).mockReturnValue({ data: alice } as unknown as ReturnType<
    typeof useMe
  >);
  render(<AccountMenu />, { wrapper });
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  signoutRedirect.mockClear();
  document.documentElement.className = "";
  // ThemeProvider persists the active theme to localStorage, and that - not the
  // className reset above - is what actually survives a remount. Without this,
  // the theme test only passes because it happens to run last in this file.
  localStorage.clear();
  // Scoped into an organization by default; only the org-less test below overrides
  // it, so most tests don't have to restate a current organization every time.
  mockUseCurrentOrganization.mockReturnValue({
    data: { id: "org-1", name: "Acme", kind: "ENTERPRISE" },
  } as never);
});

describe("AccountMenu settings entries", () => {
  it("offers both settings surfaces to a member scoped into an organization, at their real paths", async () => {
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: /Account settings/ }),
    ).toHaveAttribute("href", "/settings/account");
    expect(
      screen.getByRole("menuitem", { name: /Organization settings/ }),
    ).toHaveAttribute("href", "/settings/organization");
  });

  it("offers organization settings to a plain member with no admin permission too", async () => {
    // The tenant section it opens on is ungated by design: switching tenants moved
    // here off the rail chip, so a plain member must still be able to reach it.
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: /Organization settings/ }),
    ).toBeInTheDocument();
  });

  it("titles the row Community settings for a hub", async () => {
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "org-1", name: "Acme Hub", kind: "HUB" },
    } as never);
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: "Community settings" }),
    ).toBeInTheDocument();
  });

  it("hides organization settings from an org-less member (the Discover lobby)", async () => {
    mockUseCurrentOrganization.mockReturnValue({ data: undefined } as never);
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: /Account settings/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("menuitem", { name: /organization settings/i }),
    ).toBeNull();
  });

  it("carries Documentation, and no Support where the edition supplies no help", async () => {
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: /Documentation/ }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: /Support/ })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: /Get help/ })).toBeNull();
  });

  it("holds the theme item beside Log out, both real menu items", async () => {
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.getByRole("menuitem", { name: /(Light|Dark) theme/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: /Log out/ }),
    ).toBeInTheDocument();
  });

  it("no longer nests the data rights behind a submenu", async () => {
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(
      screen.queryByRole("menuitem", { name: /Download my data/ }),
    ).toBeNull();
    expect(
      screen.queryByRole("menuitem", { name: /Delete my account/ }),
    ).toBeNull();
  });

  it("changes the theme when its item is chosen, and closes the menu doing it", async () => {
    // react-aria closes a menu on any item's action, the theme item included - this
    // is the documented behaviour, not something worked around here.
    renderMenu();
    await user.click(screen.getByRole("button", { name: "Account" }));
    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      false,
    );

    await user.click(screen.getByRole("menuitem", { name: /Dark theme/ }));

    expect(document.documentElement.classList.contains("theme-dark")).toBe(
      true,
    );
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("reaches Log out from a fully keyboard-opened menu and signs out", async () => {
    // Enter on the trigger opens the menu via the keyboard (react-aria does not
    // autofocus an item on a pointer-opened menu, which is why the previous version
    // of this test - opening with a click - could never have caught a menu item
    // that had silently stopped being part of the arrow-key collection). End jumps
    // to the last item, the same key every ARIA menu supports for "last item";
    // reaching Log out this way is only possible if it is a real collection member.
    renderMenu();
    screen.getByRole("button", { name: "Account" }).focus();
    await user.keyboard("{Enter}");

    await user.keyboard("{End}");
    expect(screen.getByRole("menuitem", { name: /Log out/ })).toHaveFocus();

    await user.keyboard("{Enter}");

    expect(signoutRedirect).toHaveBeenCalledOnce();
  });
});
