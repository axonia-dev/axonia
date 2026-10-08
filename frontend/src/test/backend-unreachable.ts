import type { ReactNode } from "react";
import { vi } from "vitest";

/** The session module of a member signed in with these token claims, for `vi.mock("@/auth/session")`. */
export function signedInSession(claims: Record<string, unknown>) {
  return {
    AuthProvider: ({ children }: { children?: ReactNode }) => children,
    useAuth: () => ({
      isLoading: false,
      isAuthenticated: true,
      activeNavigator: undefined,
      user: {
        access_token: `h.${btoa(JSON.stringify(claims))}.s`,
        profile: { sub: claims.sub, preferred_username: "dev" },
      },
      signinRedirect: vi.fn(),
      signoutRedirect: vi.fn(),
      removeUser: vi.fn(),
    }),
  };
}

export const CURRENT_ORGANIZATION = "/organizations/current";

/** What the edge answers for every API call while the backend is down, except the paths in
 *  `answering`, which answer as a reachable backend would. */
export function backendUnreachable(answering: Record<string, unknown> = {}) {
  return vi.spyOn(globalThis, "fetch").mockImplementation((input) => {
    const url = input instanceof Request ? input.url : input.toString();
    const answer = Object.entries(answering).find(([key]) => url.endsWith(key));
    return Promise.resolve(
      answer === undefined
        ? new Response(
            JSON.stringify({
              status: 502,
              detail: "The backend is unreachable.",
            }),
            {
              status: 502,
              headers: { "content-type": "application/problem+json" },
            },
          )
        : new Response(JSON.stringify(answer[1]), {
            status: 200,
            headers: { "content-type": "application/json" },
          }),
    );
  });
}

/** Holds the next call to `path` open until the returned function answers it with `body`, so a
 *  test can look at the page while that request is in flight. Every other call answers as before. */
export function holdNextCallTo(
  fetch: ReturnType<typeof backendUnreachable>,
  path: string,
): (body: unknown) => void {
  const answerOthers = fetch.getMockImplementation();
  if (answerOthers === undefined) throw new Error("fetch has no answers");
  let answer: ((response: Response) => void) | undefined;
  const held = new Promise<Response>((resolve) => {
    answer = resolve;
  });
  let holding = true;
  fetch.mockImplementation((input) => {
    const url = input instanceof Request ? input.url : input.toString();
    if (holding && url.endsWith(path)) {
      holding = false;
      return held;
    }
    return answerOthers(input);
  });
  return (body) =>
    answer?.(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
}

export function callsTo(
  fetch: ReturnType<typeof backendUnreachable>,
  path: string,
): number {
  return fetch.mock.calls.filter(([input]) =>
    (input instanceof Request ? input.url : input.toString()).endsWith(path),
  ).length;
}

/** The answer the shell's organization read gives, of the given kind. */
export function currentOrganization(kind: "ENTERPRISE" | "HUB" = "ENTERPRISE") {
  return {
    id: "default",
    name: "Axonia",
    kind,
    memberCount: 1,
    communicationsEnabled: true,
  };
}
