import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  useApplicablePermissions,
  usePermissionCatalog,
} from "@/features/admin/ui/use-permission-catalog";
import { selfHostedEdition } from "@/selfhosted/edition";
import {
  inEdition,
  WITH_AUDIT,
  WITH_NOTIFICATIONS_CARD,
} from "@/test/edition-fixtures";

describe("the edition's permission catalog", () => {
  it("adds the group a settings section contributes", () => {
    const { result } = renderHook(() => usePermissionCatalog(), {
      wrapper: inEdition(WITH_AUDIT),
    });

    expect(result.current.groups.map((g) => g.label)).toContain("Audit");
  });

  it("adds the group an edition's Notifications card contributes, right after the group it follows", () => {
    const { result } = renderHook(() => usePermissionCatalog(), {
      wrapper: inEdition(WITH_NOTIFICATIONS_CARD),
    });

    const labels = result.current.groups.map((g) => g.label);
    expect(labels.indexOf("Ledger")).toBe(labels.indexOf("Communication") + 1);
    expect(result.current.entries.map((p) => p.code)).toContain("ledger:read");
  });

  it("is the open catalog in an edition that contributes nothing", () => {
    const { result } = renderHook(() => usePermissionCatalog());

    expect(result.current.entries.map((p) => p.code)).not.toContain(
      "audit:read",
    );
  });

  it("offers a Self-hosted installation the seven open groups, the mail server's after Communication, and no Cloud permission", () => {
    const { result } = renderHook(() => usePermissionCatalog(), {
      wrapper: inEdition(selfHostedEdition),
    });

    expect(result.current.groups.map((g) => g.label)).toEqual([
      "Workspace",
      "Communication",
      "Mail server",
      "Members",
      "Roles & access",
      "Directory sync",
      "Catalog",
      "Data",
    ]);
    expect(
      result.current.entries.filter(
        (p) => p.code.startsWith("sso:") || p.code.startsWith("billing:"),
      ),
    ).toEqual([]);
  });

  it("applies an Enterprise catalog until the organization's kind is known", () => {
    const { result } = renderHook(() => useApplicablePermissions(undefined), {
      wrapper: inEdition(WITH_AUDIT),
    });

    expect(result.current.groups.map((g) => g.label)).toContain("Audit");
  });

  it("drops a contributed enterprise-only group inside a hub", () => {
    const { result } = renderHook(() => useApplicablePermissions("HUB"), {
      wrapper: inEdition(WITH_AUDIT),
    });

    expect(result.current.groups.map((g) => g.label)).not.toContain("Audit");
  });

  it("hands back the same catalog on a re-render, so the matrix keeps its state", () => {
    const { result, rerender } = renderHook(
      () => useApplicablePermissions("ENTERPRISE"),
      { wrapper: inEdition(WITH_AUDIT) },
    );
    const first = result.current;

    rerender();

    expect(result.current).toBe(first);
  });
});
