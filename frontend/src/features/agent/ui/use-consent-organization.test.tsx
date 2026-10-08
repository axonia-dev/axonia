import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  autoEnterAttempted,
  markAutoEnterAttempted,
} from "@/auth/active-organization";

const mockEnterOrganization = vi.fn();
vi.mock("@/auth/enter-organization", () => ({
  enterOrganization: (...args: unknown[]) => {
    mockEnterOrganization(...args);
  },
}));
const mockMemberships = vi.fn();
const mockMembershipsOptions = vi.fn();
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: (options: unknown) => {
    mockMembershipsOptions(options);
    return mockMemberships() as unknown;
  },
}));
const auth = { signinRedirect: vi.fn() };
vi.mock("@/auth/session", () => ({ useAuth: () => auth }));
const mockActiveId = vi.fn<() => string | undefined>();
vi.mock("@/auth/active-organization", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  activeOrganizationId: () => mockActiveId(),
}));

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { openSlots } from "@/open-slots";
import { selfHostedEdition } from "@/selfhosted/edition";

import { useConsentOrganization } from "./use-consent-organization";

const CONSENT = "/oauth/authorize?client_id=x&state=a%26b";

function stateOf(edition: EditionSlots = openSlots) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <EditionProvider edition={edition}>
      <MemoryRouter initialEntries={[CONSENT]}>{children}</MemoryRouter>
    </EditionProvider>
  );
  return renderHook(() => useConsentOrganization(), { wrapper }).result.current
    .state;
}

function memberOf(...ids: string[]) {
  mockMemberships.mockReturnValue({
    data: ids.map((id) => ({ id, name: id })),
    isError: false,
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  mockActiveId.mockReturnValue(undefined);
  memberOf("acme");
});

describe("useConsentOrganization", () => {
  it("goes ahead when the token already holds an organization", () => {
    mockActiveId.mockReturnValue("acme");

    expect(stateOf()).toBe("held");
    expect(mockMembershipsOptions).toHaveBeenCalledWith({ enabled: false });
    expect(mockEnterOrganization).not.toHaveBeenCalled();
  });

  it("holds the installation's organization, which no token names, and enters none", () => {
    expect(stateOf(selfHostedEdition)).toBe("held");
    expect(mockEnterOrganization).not.toHaveBeenCalled();
  });

  it("enters the only organization once and comes back to this request", () => {
    expect(stateOf()).toBe("entering");
    expect(mockEnterOrganization).toHaveBeenCalledOnce();
    expect(mockEnterOrganization).toHaveBeenCalledWith(auth, "acme", CONSENT);
    expect(autoEnterAttempted()).toBe(true);
  });

  it("asks for a choice once the one entry already came back without an organization", () => {
    markAutoEnterAttempted();

    expect(stateOf()).toBe("choose");
    expect(mockEnterOrganization).not.toHaveBeenCalled();
  });

  it("asks for a choice between several organizations", () => {
    memberOf("acme", "globex");

    expect(stateOf()).toBe("choose");
    expect(mockEnterOrganization).not.toHaveBeenCalled();
  });

  it("says there is none when the member belongs to no organization", () => {
    memberOf();

    expect(stateOf()).toBe("none");
  });

  it("waits for the memberships", () => {
    mockMemberships.mockReturnValue({ data: undefined, isError: false });

    expect(stateOf()).toBe("entering");
  });

  it("fails when the memberships cannot be read", () => {
    mockMemberships.mockReturnValue({
      data: undefined,
      isError: true,
      errorUpdateCount: 1,
    });

    expect(stateOf()).toBe("failed");
  });

  it("stays failed while the memberships are asked for again", () => {
    mockMemberships.mockReturnValue({
      data: undefined,
      isError: false,
      errorUpdateCount: 1,
    });

    expect(stateOf()).toBe("failed");
  });
});
