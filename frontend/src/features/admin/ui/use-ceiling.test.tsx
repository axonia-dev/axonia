import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { useCeiling } from "@/features/admin/ui/use-ceiling";
import { openSlots } from "@/open-slots";
import { selfHostedEdition } from "@/selfhosted/edition";

vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

const HELD = new Set(["member:read"]);

const WITH_SSO: EditionSlots = {
  ...openSlots,
  settingsSections: [
    {
      group: { label: "Single sign-on", items: [] },
      pages: {},
      permissionGroup: {
        follows: "Roles & access",
        permissions: [
          {
            code: "sso:read",
            group: "Single sign-on",
            action: "read",
            enterpriseOnly: true,
            label: "View single sign-on",
            description: "See how members sign in.",
          },
        ],
      },
    },
  ],
};

function inEdition(edition: EditionSlots) {
  return function EditionWrapper({
    children,
  }: Readonly<{ children: ReactNode }>) {
    return <EditionProvider edition={edition}>{children}</EditionProvider>;
  };
}

beforeEach(() => {
  vi.mocked(useHeldPermissions).mockReturnValue(HELD);
});

describe("the caller's ceiling", () => {
  it("holds what the account holds, over every code a Self-hosted installation knows and nothing it does not", () => {
    // when
    const { result } = renderHook(() => useCeiling(), {
      wrapper: inEdition(selfHostedEdition),
    });

    // then
    expect(result.current.held).toBe(HELD);
    expect(result.current.known.has("scim:write")).toBe(true);
    expect(result.current.known.has("community:create")).toBe(true);
    expect(result.current.known.has("sso:read")).toBe(false);
  });

  it("knows a code the edition's settings sections contribute", () => {
    // when
    const { result } = renderHook(() => useCeiling(), {
      wrapper: inEdition(WITH_SSO),
    });

    // then
    expect(result.current.known.has("sso:read")).toBe(true);
  });
});
