import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { EditionProvider } from "@/edition-context";
import { selfHostedEdition } from "@/selfhosted/edition";
import {
  signInOrgless,
  signInScopedTo,
  signOutOfTest,
} from "@/test/access-token";

const mockMemberships = vi.fn();
const mockMembershipsOptions = vi.fn();
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: (options: unknown) => {
    mockMembershipsOptions(options);
    return mockMemberships() as unknown;
  },
}));

import { useActiveOrganizationId } from "./use-active-organization-id";

function installation({ children }: { children: ReactNode }) {
  return (
    <EditionProvider edition={selfHostedEdition}>{children}</EditionProvider>
  );
}

beforeEach(() => {
  mockMemberships.mockReturnValue({ data: [{ id: "acme", name: "Acme" }] });
});

afterEach(signOutOfTest);

describe("useActiveOrganizationId", () => {
  it("is the token's organization under token scope, without asking for memberships", () => {
    // given
    signInScopedTo("globex");

    // when
    const { result } = renderHook(() => useActiveOrganizationId());

    // then
    expect(result.current).toBe("globex");
    expect(mockMembershipsOptions).toHaveBeenLastCalledWith({ enabled: false });
  });

  it("is the installation's one membership, which the token never names", () => {
    // given
    signInOrgless();

    // when
    const { result } = renderHook(() => useActiveOrganizationId(), {
      wrapper: installation,
    });

    // then
    expect(result.current).toBe("acme");
    expect(mockMembershipsOptions).toHaveBeenLastCalledWith({ enabled: true });
  });

  it("is not known while the installation's membership is loading", () => {
    // given
    signInOrgless();
    mockMemberships.mockReturnValue({ data: undefined });

    // when
    const { result } = renderHook(() => useActiveOrganizationId(), {
      wrapper: installation,
    });

    // then
    expect(result.current).toBeUndefined();
  });
});
