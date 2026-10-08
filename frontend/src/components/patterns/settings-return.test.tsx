import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter, Navigate, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import {
  forgetAppLocation,
  useAppReturnPath,
  useRememberAppLocation,
} from "@/components/patterns/settings-return";

/** Mounts the hook at one location, as a member arriving there. */
function visit(path: string) {
  renderHook(() => useRememberAppLocation(), {
    wrapper: ({ children }: Readonly<{ children: ReactNode }>) => (
      <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
    ),
  });
}

/** The same, for a location whose route immediately replaces itself with another. */
function redirectFrom(from: string, to: string) {
  renderHook(() => useRememberAppLocation(), {
    wrapper: ({ children }: Readonly<{ children: ReactNode }>) => (
      <MemoryRouter initialEntries={[from]}>
        <Routes>
          <Route path={from} element={<Navigate to={to} replace />} />
          <Route path={to} element={<p>arrived</p>} />
        </Routes>
        {children}
      </MemoryRouter>
    ),
  });
}

/** What the way out of settings would point at right now. */
function returnPath(fallback: string): string {
  return renderHook(() => useAppReturnPath(fallback)).result.current;
}

describe("settings-return", () => {
  beforeEach(forgetAppLocation);

  it("falls back until somewhere has been visited", () => {
    expect(returnPath("/communities")).toBe("/communities");
  });

  it("returns to the page the member was on, query string and all", () => {
    visit("/communities/acme/events?tab=past");
    expect(returnPath("/communities")).toBe(
      "/communities/acme/events?tab=past",
    );
  });

  it("does not offer a settings page as somewhere to come back to", () => {
    visit("/communities/acme/events");
    visit("/settings/organization/users");
    visit("/settings/account/profile");
    expect(returnPath("/communities")).toBe("/communities/acme/events");
  });

  it("does not offer a docs page as somewhere to come back to", () => {
    visit("/communities/acme/events");
    visit("/docs/agent-overview");
    expect(returnPath("/communities")).toBe("/communities/acme/events");
  });

  it("does not offer the bare docs index either, the address the account menu actually links to", () => {
    visit("/communities/acme/events");
    visit("/docs");
    expect(returnPath("/communities")).toBe("/communities/acme/events");
  });

  it("keeps recording a community's own settings pages, which are app pages", () => {
    // `/communities/:slug/settings/policy` is CommunityShell's, not this surface - the
    // check keys on the catalogue's own surface paths rather than the word "settings".
    visit("/communities/acme/settings/policy");
    expect(returnPath("/communities")).toBe(
      "/communities/acme/settings/policy",
    );
  });

  it("does not offer a hub's community-scoped settings either", () => {
    visit("/communities/acme/events");
    visit("/communities/acme/settings/organization/users");
    expect(returnPath("/communities")).toBe("/communities/acme/events");
  });

  it("does not offer the consent page as somewhere to come back to", () => {
    visit("/communities/acme/events");
    visit("/oauth/authorize?client_id=x");
    expect(returnPath("/communities")).toBe("/communities/acme/events");
  });

  it("does not offer a path that redirected into settings", () => {
    // `/admin/users` is not itself a settings path, so it is recorded before its own
    // redirect fires. Returning there would only redirect back in - a trap with no way
    // out. The replace that erased that entry takes the return point with it.
    redirectFrom("/admin/users", "/settings/organization/users");
    expect(returnPath("/communities")).toBe("/communities");
  });

  it("keeps the return point through settings' own landing redirect", () => {
    // That redirect replaces a settings URL, not the app page the member came from.
    visit("/me/saved");
    redirectFrom("/settings/organization", "/settings/organization/general");
    expect(returnPath("/communities")).toBe("/me/saved");
  });
});
