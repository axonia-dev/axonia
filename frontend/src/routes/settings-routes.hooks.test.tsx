import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { EditionSlots } from "@/edition";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { openSlots } from "@/open-slots";
import { useOrganizationSettingsGroups } from "@/routes/settings-routes.hooks";
import { inEdition, WITH_NOTIFICATIONS_CARD } from "@/test/edition-fixtures";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => false,
}));

beforeEach(() => {
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: { id: "org-1", name: "Acme", kind: "ENTERPRISE" },
    isError: false,
  } as never);
  vi.mocked(usePermissions).mockReturnValue(
    permissionState((permission) => permission === "ledger:read"),
  );
});

function offersNotifications(edition: EditionSlots): boolean {
  const { result } = renderHook(() => useOrganizationSettingsGroups(), {
    wrapper: inEdition(edition),
  });
  return result.current.groups
    .flatMap((group) => group.items)
    .some((item) => item.path === "notifications");
}

describe("the organization rail's Notifications entry", () => {
  it("is offered to a holder of what the edition's Notifications card reveals", () => {
    // when
    const actual = offersNotifications(WITH_NOTIFICATIONS_CARD);

    // then
    expect(actual).toBe(true);
  });

  it("is not offered to that holder in an edition with no card", () => {
    // when
    const actual = offersNotifications(openSlots);

    // then
    expect(actual).toBe(false);
  });
});
