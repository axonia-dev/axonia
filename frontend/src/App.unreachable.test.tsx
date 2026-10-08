import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";
import { queryClient } from "@/lib/query-client";
import { selfHostedEdition } from "@/selfhosted/edition";
import {
  backendUnreachable,
  callsTo,
  CURRENT_ORGANIZATION,
  currentOrganization,
  holdNextCallTo,
} from "@/test/backend-unreachable";

vi.mock("@/auth/session", async () =>
  (await import("@/test/backend-unreachable")).signedInSession({
    sub: "dev-user",
  }),
);

/** The shell's organization and the caller's permissions answer; nothing else does. */
const ORGANIZATION_ONLY = {
  [CURRENT_ORGANIZATION]: currentOrganization(),
  "/access/me": { permissions: ["role:read", "role:assign"] },
};

beforeEach(() => {
  queryClient.setDefaultOptions({ queries: { retry: false } });
});

afterEach(() => {
  queryClient.clear();
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

describe("App with the backend unreachable", () => {
  it("says the organization could not load, rather than staying blank", async () => {
    // given
    backendUnreachable();
    window.history.pushState({}, "", "/communities");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the organization.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  it("asks for the organization once, rather than again on every remount", async () => {
    // given
    const fetch = backendUnreachable();
    window.history.pushState({}, "", "/communities");

    // when
    render(<App edition={selfHostedEdition} />);
    await screen.findByRole("alert");
    await new Promise((resolve) => setTimeout(resolve, 200));

    // then
    expect(callsTo(fetch, CURRENT_ORGANIZATION)).toBe(1);
  });

  it.each([
    ["/communities", () => screen.findAllByLabelText("Organization: Axonia")],
    [
      "/settings/organization",
      () => screen.findAllByRole("link", { name: "General" }),
    ],
  ])(
    "stays on the failure at %s while it asks again, then opens once the organization answers",
    async (path, findShell) => {
      // given
      const fetch = backendUnreachable();
      window.history.pushState({}, "", path);
      render(<App edition={selfHostedEdition} />);
      await screen.findByRole("alert");
      const answer = holdNextCallTo(fetch, CURRENT_ORGANIZATION);

      // when
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));
      await waitFor(() => {
        expect(callsTo(fetch, CURRENT_ORGANIZATION)).toBe(2);
      });

      // then
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Could not load the organization.",
      );
      expect(screen.getByRole("button", { name: "Try again" })).toHaveAttribute(
        "data-pending",
        "true",
      );
      answer(currentOrganization());
      expect(await findShell()).not.toHaveLength(0);
      expect(
        screen.queryByText("Could not load the organization."),
      ).not.toBeInTheDocument();
    },
  );

  it("says the organization could not load on its settings", async () => {
    // given
    backendUnreachable();
    window.history.pushState({}, "", "/settings/organization");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the organization.",
    );
  });

  it("offers to load a community again", async () => {
    // given
    const fetch = backendUnreachable();
    window.history.pushState({}, "", "/communities/anything");
    render(<App edition={selfHostedEdition} />);
    await screen.findByRole("alert");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    await waitFor(() => {
      expect(callsTo(fetch, "/communities/anything")).toBe(2);
    });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this community.",
    );
  });
});

describe("App with only the organization answering", () => {
  it("says the account could not load on its profile, rather than an empty page", async () => {
    // given
    backendUnreachable(ORGANIZATION_ONLY);
    window.history.pushState({}, "", "/settings/account/profile");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load this account.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });

  it("says the organization could not load on its General page, rather than an empty page", async () => {
    // given
    backendUnreachable(ORGANIZATION_ONLY);
    window.history.pushState({}, "", "/settings/organization/general");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load this organization.",
    );
    expect(screen.getByRole("heading", { name: "Organization" })).toBeVisible();
  });

  it("says the directory groups could not load, rather than loading forever", async () => {
    // given
    backendUnreachable(ORGANIZATION_ONLY);
    window.history.pushState({}, "", "/settings/organization/group-access");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load the directory groups.",
    );
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
  });

  it("names the organization in the rail when its memberships could not load", async () => {
    // given
    backendUnreachable(ORGANIZATION_ONLY);
    window.history.pushState({}, "", "/communities");

    // when
    render(<App edition={selfHostedEdition} />);

    // then
    expect(
      await screen.findAllByLabelText("Organization: Axonia"),
    ).not.toHaveLength(0);
  });
});

describe("App after a failed permissions read", () => {
  it("heals on the next page once the backend answers again, without a reload", async () => {
    // given
    const answering: Record<string, unknown> = {
      [CURRENT_ORGANIZATION]: currentOrganization(),
    };
    const fetch = backendUnreachable(answering);
    window.history.pushState({}, "", "/communities");
    render(<App edition={selfHostedEdition} />);
    await waitFor(() => {
      expect(callsTo(fetch, "/access/me")).toBeGreaterThan(0);
    });
    await new Promise((resolve) => setTimeout(resolve, 200));
    const failedCalls = callsTo(fetch, "/access/me");
    answering["/access/me"] = { permissions: ["member:read"] };

    // when
    act(() => {
      window.history.pushState({}, "", "/settings/organization/users");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    // then
    expect(await screen.findByText("Could not load the users.")).toBeVisible();
    expect(
      screen.getByRole("heading", { level: 1, name: "Users" }),
    ).toBeVisible();
    expect(
      screen.queryByText("Could not load the permissions."),
    ).not.toBeInTheDocument();
    expect(callsTo(fetch, "/access/me")).toBe(failedCalls + 1);
  });
});
