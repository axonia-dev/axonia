import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { stubClipboard } from "@/test/clipboard";
import { ThemeProvider } from "@/theme/ThemeProvider";

import { DocPageView } from "./DocPageView";
import { DocsShell } from "./DocsShell";

vi.mock("@/auth/session", () => ({
  useAuth: () => ({
    user: { profile: { preferred_username: "dev", sub: "dev-user" } },
    signoutRedirect: vi.fn(),
  }),
}));

vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({ data: undefined }),
}));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({ data: undefined }),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  stubClipboard({ writeText: vi.fn().mockResolvedValue(undefined) });
});

function renderShellAt(path: string) {
  return render(
    <ThemeProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/docs" element={<DocsShell />}>
              <Route path=":slug" element={<DocPageView />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
}

describe("DocsShell", () => {
  it("offers no Get help where the edition supplies none", () => {
    renderShellAt("/docs/connect-your-agent");
    expect(
      screen.getAllByRole("link", { name: "Overview" }).length,
    ).toBeGreaterThan(0);
    expect(
      screen.queryByRole("button", { name: "Get help" }),
    ).not.toBeInTheDocument();
  });

  it("renders the nav and the routed page together", () => {
    renderShellAt("/docs/connect-your-agent");
    // The nav appears twice - the desktop sidebar and the mobile disclosure -
    // each responsible for one breakpoint.
    expect(
      screen.getAllByRole("link", { name: "Overview" }).length,
    ).toBeGreaterThan(0);
    expect(
      screen.getByRole("heading", { name: /connect an agent/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it("lists the page's h2 sections in the on-this-page rail", () => {
    renderShellAt("/docs/connect-your-agent");
    expect(
      screen.getByRole("link", { name: /configure the client/i }),
    ).toHaveAttribute("href", "#configure-the-client");
  });

  it("offers a way back out, in the top bar it now owns", () => {
    renderShellAt("/docs/connect-your-agent");
    expect(
      screen.getByRole("link", { name: /back to the app/i }),
    ).toBeInTheDocument();
  });

  it("resets its own scroll on navigating to another page, since it owns the scroll container that used to be the host shell's <main>", async () => {
    renderShellAt("/docs/agent-overview");
    const scrollContainer = screen.getByTestId("docs-scroll");
    scrollContainer.scrollTop = 400;
    expect(scrollContainer.scrollTop).toBe(400);

    // The desktop sidebar's link, first among the two DocsNav renders one page down.
    await user.click(
      screen.getAllByRole("link", { name: /connect an agent/i })[0]!,
    );

    expect(scrollContainer.scrollTop).toBe(0);
  });
});
