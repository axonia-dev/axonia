import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, onTestFinished, vi } from "vitest";

import { SettingsShell } from "@/components/patterns/SettingsShell";
import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { useOnboarding } from "@/features/onboarding/api/use-onboarding";
import { signInScopedToForEachTest } from "@/test/access-token";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Split out of SettingsShell.test.tsx, which sits on the 300-line ceiling: the controls pinned to
// the right of both top bars are a concern of their own, and the account-menu assertion moved here
// from that file to sit beside the bell it now shares the corner with.
//
// The rail and the account menu are stubbed to nothing rather than fed: this file is about which
// controls are present in which bar, so everything they would otherwise need (auth, /me,
// memberships, permissions) is beside the point. Only the bell renders for real.
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({ data: { kind: "ENTERPRISE" } }),
}));
vi.mock("@/features/admin/ui/OrganizationSwitcher", () => ({
  OrganizationSwitcher: () => null,
}));
vi.mock("@/features/identity/ui/AccountMenu", () => ({
  AccountMenu: () => <button type="button">Account</button>,
}));
vi.mock("@/features/communication/api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: 4 }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: vi.fn(() => ({ data: undefined })),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));

signInScopedToForEachTest("org-1");

const BELL = { name: "Notifications, 4 unread" };

function renderShell() {
  return render(
    <ThemeProvider>
      <SidebarProvider>
        <MemoryRouter initialEntries={["/settings/account/profile"]}>
          <Routes>
            <Route
              element={
                <SettingsShell
                  base="/settings/account"
                  title="Account settings"
                  backTo="/communities"
                  backTabLabel="Communities"
                  groups={[]}
                />
              }
            >
              <Route
                path="/settings/account/profile"
                element={<p>Profile page</p>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </SidebarProvider>
    </ThemeProvider>,
  );
}

const desktopBar = () => within(screen.getByTestId("settings-desktop-topbar"));
const mobileBar = () => within(screen.getByTestId("settings-topbar"));

describe("the SettingsShell top bars", () => {
  it("carries the account menu in the mobile top bar, beside the way out", () => {
    renderShell();
    expect(
      mobileBar().getByRole("button", { name: "Account" }),
    ).toBeInTheDocument();
  });

  // Settings is a mode, not a place to be cut off in. A member reading a notification badge has to
  // find it in the same corner wherever they are, or they will not look for it at all.
  it("carries the notification bell in both top bars, before the account menu", () => {
    renderShell();
    expect(mobileBar().getByRole("button", BELL)).toBeInTheDocument();
    const bell = desktopBar().getByRole("button", BELL);
    const account = desktopBar().getByRole("button", { name: "Account" });
    // DOCUMENT_POSITION_FOLLOWING: the account menu comes after the bell in the DOM.
    expect(
      bell.compareDocumentPosition(account) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  // Ordering is a stated requirement and otherwise gate-invisible: nothing else in the suite
  // renders the chip and the bell together.
  it("carries the getting-started chip before the notification bell, in both top bars", () => {
    // SettingsShell reads useOnboarding once for its own recorder call, and
    // OnboardingAffordance reads it again in each of the two header slots: every
    // call within this render must see the same OPEN state, so `mockReturnValue`
    // rather than the "once" variant.
    vi.mocked(useOnboarding).mockReturnValue({
      data: {
        state: "OPEN",
        path: {
          variant: "MEMBER",
          communitySlug: "guild",
          steps: [{ key: "HEALTH", route: "/x", detail: null, visited: false }],
        },
      },
    } as unknown as ReturnType<typeof useOnboarding>);

    try {
      renderShell();

      const chip = { name: /getting started/i };
      expect(mobileBar().getByRole("button", chip)).toBeInTheDocument();
      const desktopChip = desktopBar().getByRole("button", chip);
      const bell = desktopBar().getByRole("button", BELL);
      expect(
        desktopChip.compareDocumentPosition(bell) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    } finally {
      vi.mocked(useOnboarding).mockReturnValue({
        data: undefined,
      } as unknown as ReturnType<typeof useOnboarding>);
    }
  });
});

const FOCUSABLE_WARNING =
  "<Focusable> child must be focusable. Please ensure the tabIndex prop is passed through.";

/** jsdom applies no Tailwind, so the breakpoint's own hiding rule is added by hand. */
function hideWithCss(rule: string) {
  const style = document.createElement("style");
  style.textContent = rule;
  document.head.append(style);
  onTestFinished(() => style.remove());
}

function spyOnWarnings() {
  const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
  onTestFinished(() => warn.mockRestore());
  return warn;
}

// react-aria warns when a tooltip trigger mounts where it cannot be focused, and the bar a
// breakpoint hides is exactly that: its way out would be a tooltip trigger nobody can reach.
describe("the SettingsShell way-out tooltip", () => {
  it("mounts no unreachable tooltip trigger at desktop width", () => {
    hideWithCss(".md\\:hidden { display: none; }");
    const warn = spyOnWarnings();

    renderShell();

    expect(warn).not.toHaveBeenCalledWith(FOCUSABLE_WARNING);
  });

  it("mounts no unreachable tooltip trigger at phone width", () => {
    forceMobileViewport();
    onTestFinished(restoreViewport);
    hideWithCss(".hidden { display: none; }");
    const warn = spyOnWarnings();

    renderShell();

    expect(warn).not.toHaveBeenCalledWith(FOCUSABLE_WARNING);
  });
});
