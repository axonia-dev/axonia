import { render, screen, within } from "@testing-library/react";
import type { PropsWithChildren, ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";
import type { Edition } from "@/edition";
import { selfHostedEdition } from "@/selfhosted/edition";

const signinRedirect = vi.fn();
const mockUseAuth = vi.fn();

vi.mock("@/auth/session", () => ({
  AuthProvider: ({ children }: { children?: ReactNode }) => children,
  useAuth: () => mockUseAuth() as unknown,
}));

function fakeJwt(claims: Record<string, unknown>): string {
  return `h.${btoa(JSON.stringify(claims))}.s`;
}

const ANONYMOUS = {
  isLoading: false,
  isAuthenticated: false,
  activeNavigator: undefined,
  user: undefined,
  signinRedirect,
  signoutRedirect: vi.fn(),
  removeUser: vi.fn(),
};

const SIGNED_IN = {
  ...ANONYMOUS,
  isAuthenticated: true,
  user: {
    access_token: fakeJwt({ sub: "dev-user", org: "default" }),
    profile: { sub: "dev-user", preferred_username: "dev" },
  },
};

const PROBE = [{ path: "/probe", element: <p>probe page</p> }];

function ProbeRootRedirect() {
  return <p>edition root</p>;
}

function ProbeFrame({ children }: Readonly<PropsWithChildren>) {
  return <section aria-label="edition frame">{children}</section>;
}

function editionWith(
  routes: Pick<Edition, "publicRoutes" | "authenticatedRoutes">,
): Edition {
  return { ...selfHostedEdition, ...routes };
}

beforeEach(() => {
  sessionStorage.clear();
  signinRedirect.mockClear();
  mockUseAuth.mockReturnValue(ANONYMOUS);
});

afterEach(() => {
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

describe("App with an edition", () => {
  it("frames a public route in the frame the edition gives", () => {
    // given
    window.history.pushState({}, "", "/probe");
    const edition: Edition = {
      ...editionWith({ publicRoutes: PROBE, authenticatedRoutes: [] }),
      AppFrame: ProbeFrame,
    };

    // when
    render(<App edition={edition} />);

    // then
    const frame = screen.getByRole("region", { name: "edition frame" });
    expect(within(frame).getByText("probe page")).toBeInTheDocument();
  });

  it("frames an authenticated route in the same frame, around the sign-in gate", () => {
    // given
    window.history.pushState({}, "", "/probe");
    const edition: Edition = {
      ...editionWith({ publicRoutes: [], authenticatedRoutes: PROBE }),
      AppFrame: ProbeFrame,
    };

    // when
    render(<App edition={edition} />);

    // then - the anonymous visitor is held at the gate, inside the frame
    const frame = screen.getByRole("region", { name: "edition frame" });
    expect(within(frame).getByText("Signing in…")).toBeInTheDocument();
    expect(signinRedirect).toHaveBeenCalledOnce();
  });

  it("lands a signed-in member at the root where the edition's root redirect says", () => {
    // given
    vi.spyOn(globalThis, "fetch").mockReturnValue(
      new Promise<Response>(() => {}),
    );
    mockUseAuth.mockReturnValue(SIGNED_IN);
    window.history.pushState({}, "", "/");
    const edition: Edition = {
      ...editionWith({ publicRoutes: [], authenticatedRoutes: [] }),
      RootRedirect: ProbeRootRedirect,
    };

    // when
    render(<App edition={edition} />);

    // then
    expect(screen.getByText("edition root")).toBeInTheDocument();
  });

  it("answers not found to a signed-in member at a page only the Cloud routes", () => {
    // given - the Discover lobby is an authenticated route of the Cloud edition alone
    vi.spyOn(globalThis, "fetch").mockReturnValue(
      new Promise<Response>(() => {}),
    );
    mockUseAuth.mockReturnValue(SIGNED_IN);
    window.history.pushState({}, "", "/discover");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(
      screen.getByRole("heading", { name: "Page not found" }),
    ).toBeInTheDocument();
  });

  it("serves a public route the edition gives, with no sign-in", () => {
    // given
    window.history.pushState({}, "", "/probe");

    // when
    render(
      <App
        edition={editionWith({ publicRoutes: PROBE, authenticatedRoutes: [] })}
      />,
    );

    // then
    expect(screen.getByText("probe page")).toBeInTheDocument();
    expect(signinRedirect).not.toHaveBeenCalled();
  });

  it("sends an anonymous visitor to sign in before an authenticated route the edition gives", () => {
    // given
    window.history.pushState({}, "", "/probe");

    // when
    render(
      <App
        edition={editionWith({ publicRoutes: [], authenticatedRoutes: PROBE })}
      />,
    );

    // then
    expect(screen.queryByText("probe page")).not.toBeInTheDocument();
    expect(signinRedirect).toHaveBeenCalledOnce();
  });

  it("renders an authenticated route the edition gives to a signed-in member", () => {
    // given
    mockUseAuth.mockReturnValue(SIGNED_IN);
    window.history.pushState({}, "", "/probe");

    // when
    render(
      <App
        edition={editionWith({ publicRoutes: [], authenticatedRoutes: PROBE })}
      />,
    );

    // then
    expect(screen.getByText("probe page")).toBeInTheDocument();
  });

  it("does not serve a Cloud page to an edition that was not given it", () => {
    // given - the pricing page is public in the Cloud edition only
    window.history.pushState({}, "", "/pricing");

    // when
    render(<App edition={selfHostedEdition} />);

    // then - the path falls through to the authenticated table, which asks for a sign-in
    expect(signinRedirect).toHaveBeenCalledOnce();
  });

  it("sends an anonymous visitor at the root to sign in rather than to a landing page", () => {
    // given
    window.history.pushState({}, "", "/");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(signinRedirect).toHaveBeenCalledOnce();
  });

  it("keeps the unsubscribe link public in an edition with no public routes", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(null, { status: 204 }),
    );
    window.history.pushState({}, "", "/unsubscribe?token=t");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(
      await screen.findByRole("heading", { name: "Unsubscribed" }),
    ).toBeInTheDocument();
    expect(signinRedirect).not.toHaveBeenCalled();
  });
});
