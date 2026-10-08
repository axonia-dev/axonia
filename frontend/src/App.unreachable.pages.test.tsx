import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";
import { queryClient } from "@/lib/query-client";
import { selfHostedEdition } from "@/selfhosted/edition";
import {
  backendUnreachable,
  callsTo,
  CURRENT_ORGANIZATION,
  currentOrganization,
} from "@/test/backend-unreachable";

vi.mock("@/auth/session", async () =>
  (await import("@/test/backend-unreachable")).signedInSession({
    sub: "dev-user",
  }),
);

const EVERY_PERMISSION = [
  "community_kind:read",
  "library_type:read",
  "section_type:read",
  "reaction:read",
  "member:read",
  "role:read",
  "scim:read",
  "scim:write",
];

/** The shell's organization and every permission answer; each page's own read does not. */
const PERMISSIONS_ANSWERING = {
  [CURRENT_ORGANIZATION]: currentOrganization(),
  "/access/me": { permissions: EVERY_PERMISSION },
};

beforeEach(() => {
  queryClient.setDefaultOptions({ queries: { retry: false } });
});

afterEach(() => {
  queryClient.clear();
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

function visit(path: string) {
  window.history.pushState({}, "", path);
  render(<App edition={selfHostedEdition} />);
}

async function expectFailure(heading: string, message: string) {
  expect(await screen.findByRole("alert")).toHaveTextContent(message);
  expect(
    screen.getByRole("heading", { level: 1, name: heading }),
  ).toBeVisible();
  expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
}

describe("A settings page whose own read fails", () => {
  it.each([
    ["users", "Users", "Could not load the users.", "No users yet."],
    ["roles", "Roles", "Could not load the roles.", "No role is open."],
    [
      "directory-sync",
      "Directory sync",
      "Could not load the SCIM tokens.",
      "No SCIM token configured for this organization.",
    ],
  ])(
    "says /settings/organization/%s could not load, never as empty",
    async (path, heading, message, empty) => {
      // given
      backendUnreachable(PERMISSIONS_ANSWERING);

      // when
      visit(`/settings/organization/${path}`);

      // then
      await expectFailure(heading, message);
      expect(screen.queryByText(empty)).not.toBeInTheDocument();
    },
  );

  it("offers no SCIM token to mint while the tokens are unread", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/settings/organization/directory-sync");

    // then
    await expectFailure("Directory sync", "Could not load the SCIM tokens.");
    expect(
      screen.queryByRole("button", { name: /token/ }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ["community-types", "Could not load the community types."],
    ["library-types", "Could not load the library types."],
    ["section-types", "Could not load the section types."],
    ["reactions", "Could not load the reactions."],
  ])(
    "says the %s vocabulary could not load, keeping its tabs",
    async (tab, message) => {
      // given
      backendUnreachable(PERMISSIONS_ANSWERING);

      // when
      visit(`/settings/organization/vocabulary/${tab}`);

      // then
      await expectFailure("Vocabulary", message);
      expect(
        screen.getByRole("navigation", { name: "Vocabulary catalogues" }),
      ).toBeVisible();
      expect(window.location.pathname).toBe(
        `/settings/organization/vocabulary/${tab}`,
      );
    },
  );

  it("says the saved items could not load, never as nothing saved", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/me/saved");

    // then
    await expectFailure("Saved", "Could not load the saved items.");
    expect(screen.queryByText("Nothing saved yet")).not.toBeInTheDocument();
  });

  it("says the contributions could not load, claiming none", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/me/impact");

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load these contributions.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    expect(
      screen.queryByText(
        "Contributions appear here as people appreciate them.",
      ),
    ).not.toBeInTheDocument();
  });
});

describe("A settings page whose permissions read fails", () => {
  it.each([
    ["vocabulary/library-types", "Vocabulary"],
    ["users", "Users"],
    ["roles", "Roles"],
    ["directory-sync", "Directory sync"],
    ["group-access", "Group access"],
    ["data-export", "Data export"],
    ["notifications", "Notifications"],
  ])(
    "says /settings/organization/%s could not load the permissions, staying put",
    async (path, heading) => {
      // given
      const fetch = backendUnreachable({
        [CURRENT_ORGANIZATION]: currentOrganization(),
      });

      // when
      visit(`/settings/organization/${path}`);

      // then
      await screen.findByRole("alert");
      await new Promise((resolve) => setTimeout(resolve, 200));
      await expectFailure(heading, "Could not load the permissions.");
      // The shell's observers mount after the failure and ask once more, then it settles.
      expect(callsTo(fetch, "/access/me")).toBe(2);
      expect(window.location.pathname).toBe(`/settings/organization/${path}`);
    },
  );
});

describe("An account page offered only on an answer", () => {
  it("says the API keys could not load when the offer read fails, rather than leaving", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/settings/account/api-keys");

    // then
    await expectFailure("API keys", "Could not load the API keys.");
    expect(window.location.pathname).toBe("/settings/account/api-keys");
  });

  it("still leaves the API keys when agents are genuinely not offered", async () => {
    // given
    backendUnreachable({
      ...PERMISSIONS_ANSWERING,
      "/me/agent-access": { available: false },
    });

    // when
    visit("/settings/account/api-keys");

    // then
    await waitFor(() => {
      expect(window.location.pathname).toBe("/settings/account/profile");
    });
  });
});

describe("An account or activity page whose read fails", () => {
  it("says the notification preferences could not load", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/settings/account/notifications");

    // then
    await expectFailure(
      "Notifications",
      "Could not load notification preferences.",
    );
  });

  it("keeps the expertise heading while saying the areas could not load", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/me/expertise");

    // then
    await expectFailure("Me", "Could not load these expertise areas.");
    expect(screen.getByText("My expertise", { selector: "p" })).toBeVisible();
  });

  it("says the connected apps could not load, rather than hiding them", async () => {
    // given
    backendUnreachable({
      ...PERMISSIONS_ANSWERING,
      "/me/agent-access": { available: true },
    });

    // when
    visit("/settings/account/api-keys");

    // then
    expect(
      await screen.findByText("Could not load the connected apps."),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Connected apps" }),
    ).toBeVisible();
  });

  it("says an agent's request could not load, offering to try again", async () => {
    // given
    backendUnreachable(PERMISSIONS_ANSWERING);

    // when
    visit("/oauth/authorize?client_id=x");

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load this request.",
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  });
});
