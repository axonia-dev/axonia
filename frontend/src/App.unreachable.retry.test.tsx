import { render, screen, waitFor } from "@testing-library/react";
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

beforeEach(() => {
  queryClient.setDefaultOptions({ queries: { retry: false } });
});

afterEach(() => {
  queryClient.clear();
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

const PERMISSIONS_FAILED = "Could not load the permissions.";

describe("A settings page whose permissions read failed, told to try again", () => {
  it.each([
    [
      "users",
      "member:read",
      () => screen.findByText("Could not load the users."),
    ],
    [
      "vocabulary/library-types",
      "library_type:read",
      () => screen.findByText("Could not load the library types."),
    ],
    [
      "notifications",
      "communication:write",
      () =>
        screen.findByRole("switch", { name: "Email and chat notifications" }),
    ],
  ])(
    "shows /settings/organization/%s once the permissions answer, asking once more",
    async (path, permission, findPage) => {
      // given
      const fetch = backendUnreachable({
        [CURRENT_ORGANIZATION]: currentOrganization(),
      });
      window.history.pushState({}, "", `/settings/organization/${path}`);
      render(<App edition={selfHostedEdition} />);
      await screen.findByText(PERMISSIONS_FAILED);
      await new Promise((resolve) => setTimeout(resolve, 200));
      const failedCalls = callsTo(fetch, "/access/me");
      const answer = holdNextCallTo(fetch, "/access/me");

      // when
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));

      // then
      await waitFor(() => {
        expect(callsTo(fetch, "/access/me")).toBe(failedCalls + 1);
      });
      expect(screen.getByRole("button", { name: "Try again" })).toHaveAttribute(
        "data-pending",
        "true",
      );
      answer({ permissions: [permission] });
      expect(await findPage()).toBeVisible();
      expect(screen.queryByText(PERMISSIONS_FAILED)).not.toBeInTheDocument();
      expect(callsTo(fetch, "/access/me")).toBe(failedCalls + 1);
    },
  );
});
