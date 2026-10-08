import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppAuthProvider } from "@/auth/AppAuthProvider";

vi.mock("@/auth/session", () => ({
  AuthProvider: ({
    probe,
    children,
  }: {
    probe: boolean;
    children?: React.ReactNode;
  }) => (
    <div data-testid="session-provider" data-probe={String(probe)}>
      {children}
    </div>
  ),
  // The provider nests AuthScopeRecovery, which reads useAuth; a benign
  // authenticated stub keeps it inert during this provider-shape test.
  useAuth: () => ({ isAuthenticated: true, user: undefined }),
}));

afterEach(() => {
  window.history.pushState({}, "", "/");
});

describe("AppAuthProvider", () => {
  it("wraps children in the session provider", () => {
    // when
    render(<AppAuthProvider sessionlessPaths={[]}>child</AppAuthProvider>);

    // then
    expect(screen.getByTestId("session-provider")).toHaveTextContent("child");
  });

  it("asks the edge for a session on a page the edition does not list", () => {
    // given
    window.history.pushState({}, "", "/communities");

    // when
    render(
      <AppAuthProvider sessionlessPaths={["/quiet"]}>child</AppAuthProvider>,
    );

    // then
    expect(screen.getByTestId("session-provider")).toHaveAttribute(
      "data-probe",
      "true",
    );
  });

  it("never asks the edge for a session on a page the edition lists, trailing slash or not", () => {
    // given
    window.history.pushState({}, "", "/quiet/");

    // when
    render(
      <AppAuthProvider sessionlessPaths={["/quiet"]}>child</AppAuthProvider>,
    );

    // then
    expect(screen.getByTestId("session-provider")).toHaveAttribute(
      "data-probe",
      "false",
    );
  });

  it("carries no client configuration of its own, because the edge is the OAuth client now", async () => {
    // given - the authority, client id, PKCE settings and token storage that used to be declared
    // here all moved to edge/src/auth.ts. Asserted on the module rather than left to a comment,
    // because re-introducing any of them here would silently put a credential back in the browser.
    const module: Record<string, unknown> =
      await import("@/auth/AppAuthProvider");

    // then
    expect(Object.keys(module)).toEqual(["AppAuthProvider"]);
  });
});
