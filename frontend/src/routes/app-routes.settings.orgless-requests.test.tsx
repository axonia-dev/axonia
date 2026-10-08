import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes } from "react-router-dom";
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  type MockInstance,
  vi,
} from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { openSlots } from "@/open-slots";
import { SettingsRoutes } from "@/routes/settings-routes";
import { signInOrgless, signOutOfTest } from "@/test/access-token";
import { NO_AGENT_ACCESS } from "@/test/agent-access";
import { jsonResponse, urlOf } from "@/test/fetch";
import { forceMobileViewport, restoreViewport } from "@/test/viewport";
import { ThemeProvider } from "@/theme/ThemeProvider";

// Permissions, the current organization and the privacy page are all real here: the point is
// which requests an org-less mount sends, and a mocked permission hook would hide exactly that.
vi.mock("@/features/agent/api/use-personal-access-tokens", () => ({
  useAgentAccess: () => NO_AGENT_ACCESS,
}));
vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "alice", sub: "sub-1" } },
    signoutRedirect: vi.fn(),
  }),
}));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({
    data: { id: "sub-1", displayName: "Alice Anderson", email: null },
  }),
}));
vi.mock("@/features/onboarding/api/use-onboarding", () => ({
  useOnboarding: () => ({ data: undefined }),
  useRecordVisit: () => ({ mutate: vi.fn() }),
  useCloseOnboarding: () => ({ mutate: vi.fn() }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => false,
}));

let fetchSpy: MockInstance<typeof fetch>;

beforeEach(() => {
  forceMobileViewport();
  signInOrgless();
  fetchSpy = vi
    .spyOn(globalThis, "fetch")
    .mockResolvedValue(jsonResponse({}, 401));
});

afterEach(() => {
  fetchSpy.mockRestore();
  restoreViewport();
  signOutOfTest();
});

function renderAt(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <SidebarProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>{SettingsRoutes({ base: "", edition: openSlots })}</Routes>
          </MemoryRouter>
        </SidebarProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function requestedUrls(): string[] {
  return fetchSpy.mock.calls.map(([input]) => urlOf(input));
}

describe("account settings for a person with no organization, permissions unmocked", () => {
  it("opens Privacy in its personal form without asking for organization access", async () => {
    // when
    renderAt("/settings/account/privacy");

    // then
    expect(
      await screen.findByText(
        "A machine-readable copy of what is held for this account outside any community.",
      ),
    ).toBeInTheDocument();
    expect(requestedUrls()).toEqual([]);
  });

  it("sends the organization surface to account settings without a request", async () => {
    // when
    renderAt("/settings/organization");

    // then
    expect(
      await screen.findByRole("heading", { name: "Account" }),
    ).toBeInTheDocument();
    expect(requestedUrls()).toEqual([]);
  });
});
