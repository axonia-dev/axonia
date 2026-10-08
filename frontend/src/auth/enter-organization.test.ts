import { describe, expect, it, vi } from "vitest";

import {
  organizationScope,
  storedActiveOrganizationId,
} from "@/auth/active-organization";
import { enterOrganization } from "@/auth/enter-organization";

describe("enterOrganization", () => {
  it("persists the organization then re-authorizes scoped to it", () => {
    const signinRedirect = vi.fn().mockResolvedValue(undefined);

    enterOrganization({ signinRedirect }, "rust-practitioners");

    expect(storedActiveOrganizationId()).toBe("rust-practitioners");
    // The root rather than the page the caller entered from: entering is nearly always asked for
    // from the lobby, and a login that defaults to the current path would come straight back to it.
    expect(signinRedirect).toHaveBeenCalledWith({
      scope: organizationScope("rust-practitioners"),
      state: { returnTo: "/" },
    });
  });

  it("comes back to the page it was asked from", () => {
    const signinRedirect = vi.fn().mockResolvedValue(undefined);

    enterOrganization(
      { signinRedirect },
      "acme",
      "/oauth/authorize?client_id=x&state=a%26b",
    );

    expect(signinRedirect).toHaveBeenCalledWith({
      scope: organizationScope("acme"),
      state: { returnTo: "/oauth/authorize?client_id=x&state=a%26b" },
    });
  });
});
