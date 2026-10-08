import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { urlOf } from "@/test/fetch";
import { ThemeProvider } from "@/theme/ThemeProvider";

import { UnsubscribePage } from "./UnsubscribePage";

let authenticated = false;

vi.mock("@/auth/session", () => ({
  useAuth: () => ({ isAuthenticated: authenticated }),
}));

function renderUnsubscribe(
  query: string,
  options: Readonly<{ authenticated?: boolean }> = {},
) {
  authenticated = options.authenticated ?? false;
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[`/unsubscribe${query}`]}>
          <UnsubscribePage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe("UnsubscribePage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("unsubscribes on load and confirms", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    renderUnsubscribe("?token=abc");

    expect(
      await screen.findByText(/these emails will no longer be sent/i),
    ).toBeInTheDocument();
  });

  it("says so plainly when the link is not valid", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 400 }),
    );

    renderUnsubscribe("?token=broken");

    expect(
      await screen.findByText(/this link is not valid/i),
    ).toBeInTheDocument();
  });

  it("tells a rate limit apart from an invalid link", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 429 }),
    );

    renderUnsubscribe("?token=abc");

    expect(await screen.findByText(/too many attempts/i)).toBeInTheDocument();
    expect(
      screen.queryByText(/this link is not valid/i),
    ).not.toBeInTheDocument();
  });

  it("tells a server failure apart from an invalid link, as a retryable problem", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      new Response(null, { status: 502 }),
    );

    renderUnsubscribe("?token=abc");

    expect(
      await screen.findByText(/something went wrong/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/this link is not valid/i),
    ).not.toBeInTheDocument();
  });

  it("treats a dropped connection as retryable too, not as an invalid link", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(
      new TypeError("Failed to fetch"),
    );

    renderUnsubscribe("?token=abc");

    expect(
      await screen.findByText(/something went wrong/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/this link is not valid/i),
    ).not.toBeInTheDocument();
  });

  it("needs no authenticated session", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    renderUnsubscribe("?token=abc", { authenticated: false });

    expect(
      await screen.findByText(/these emails will no longer be sent/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/sign in/i)).not.toBeInTheDocument();
  });

  it("offers a way back into settings for a signed-in visitor", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    renderUnsubscribe("?token=abc", { authenticated: true });

    expect(
      await screen.findByRole("link", { name: /notification settings/i }),
    ).toHaveAttribute("href", "/settings/account/notifications");
  });

  it("offers no such link for a signed-out visitor", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );

    renderUnsubscribe("?token=abc", { authenticated: false });

    await screen.findByText(/these emails will no longer be sent/i);
    expect(
      screen.queryByRole("link", { name: /notification settings/i }),
    ).not.toBeInTheDocument();
  });

  it("sends the token from the URL, not a stale or empty one", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    renderUnsubscribe("?token=my-real-token");
    await screen.findByText(/these emails will no longer be sent/i);

    expect(urlOf(spy.mock.calls[0]![0])).toContain("token=my-real-token");
  });

  // A production mount fires exactly once - this render (like React Testing Library's
  // in general) is not wrapped in StrictMode, so it matches that, not development's
  // double-invoke (see useUnsubscribeStatus's own doc comment for why that second
  // development POST is accepted rather than guarded against).
  it("fires exactly one request per mount", async () => {
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    renderUnsubscribe("?token=abc");
    await screen.findByText(/these emails will no longer be sent/i);

    expect(spy).toHaveBeenCalledTimes(1);
  });
});
