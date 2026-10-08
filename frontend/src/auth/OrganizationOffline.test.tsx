import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OrganizationOffline } from "@/auth/OrganizationOffline";
import type { OrganizationOfflineReason } from "@/auth/token";
import { ThemeProvider } from "@/theme/ThemeProvider";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const signinRedirect = vi.fn();
const signoutRedirect = vi.fn();
const isDelegated = vi.fn(() => false);
const onRetry = vi.fn();

vi.mock("@/auth/session", () => ({
  useAuth: () => ({ signinRedirect, signoutRedirect }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => isDelegated(),
}));

afterEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  vi.clearAllMocks();
});

function renderOffline(reason: OrganizationOfflineReason = "SUSPENDED") {
  return render(
    <ThemeProvider>
      <OrganizationOffline reason={reason} onRetry={onRetry} />
    </ThemeProvider>,
  );
}

describe("OrganizationOffline, suspended", () => {
  it("names the state and offers a way out", async () => {
    window.localStorage.setItem("axonia.active-organization", "acme");

    renderOffline();

    expect(
      screen.getByRole("heading", { name: /account is suspended/i }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /choose another organization/i }),
    );

    expect(
      window.localStorage.getItem("axonia.active-organization"),
    ).toBeNull();
    expect(window.sessionStorage.getItem("axonia.auto-enter-attempted")).toBe(
      "1",
    );
    expect(signinRedirect).toHaveBeenCalledWith({
      scope: "openid profile email",
      state: { returnTo: "/" },
    });
  });

  it("marks the state as recoverable, in the warning tone rather than the danger one", () => {
    const { container } = renderOffline();

    const mark = container.querySelector("h1 svg");

    expect(mark).toHaveClass("text-warning");
    expect(mark).not.toHaveClass("text-danger");
  });

  it("signs out through the app's real logout, not the 401 plumbing", async () => {
    window.localStorage.setItem("axonia.active-organization", "acme");

    renderOffline();

    await user.click(screen.getByRole("button", { name: /sign out/i }));

    expect(
      window.localStorage.getItem("axonia.active-organization"),
    ).toBeNull();
    expect(signoutRedirect).toHaveBeenCalled();
  });

  it("offers no auth actions to a delegated tab, which has its own exit", () => {
    isDelegated.mockReturnValueOnce(true);

    renderOffline();

    expect(
      screen.queryByRole("button", { name: /choose another organization/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /sign out/i }),
    ).not.toBeInTheDocument();
  });

  it("offers a retry, because an unsuspend tells no open tab about itself", async () => {
    renderOffline();

    await user.click(screen.getByRole("button", { name: /try again/i }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

describe("OrganizationOffline, closed", () => {
  it("says the account is closed, without promising it is temporary", () => {
    renderOffline("CLOSED");

    expect(
      screen.getByRole("heading", { name: /account is closed/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/permanent/i)).toBeInTheDocument();
    expect(screen.queryByText(/temporarily offline/i)).not.toBeInTheDocument();
    expect(
      screen.queryByText(/contact your administrator/i),
    ).not.toBeInTheDocument();
  });

  it("offers no retry, because there is nothing to wait for", () => {
    renderOffline("CLOSED");

    expect(
      screen.queryByRole("button", { name: /try again/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /choose another organization/i }),
    ).toBeInTheDocument();
  });

  it("marks the state as terminal rather than recoverable", () => {
    const { container } = renderOffline("CLOSED");

    const mark = container.querySelector("h1 svg");

    expect(mark).toHaveClass("text-danger");
    expect(mark).not.toHaveClass("text-warning");
  });
});
