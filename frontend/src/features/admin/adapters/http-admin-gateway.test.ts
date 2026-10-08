import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function voidResponse(status = 204): Response {
  return new Response(null, { status });
}

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) return input.url;
  return input instanceof URL ? input.href : (input ?? "");
}

function methodOf(init: RequestInit | undefined): string {
  return init?.method ?? "GET";
}

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

const adminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  memberStatus: "PRESENT",
  effectiveRoles: [
    {
      role: "ADMIN",
      via: "DIRECT",
      bindingId: 1,
      roleId: 10,
    },
  ],
};

const roleBinding = {
  id: 1,
  subjectType: "USER",
  subjectId: "u1",
  roleId: 10,
  subjectLabel: null,
};

const idpGroup = {
  groupId: "grp-1",
  displayName: "Engineering",
  inDirectory: true,
  awaitingConfirmation: false,
  bindings: [
    {
      id: 2,
      subjectType: "GROUP",
      subjectId: "grp-1",
      roleId: 10,
      subjectLabel: "Engineering",
    },
  ],
};

const role = {
  id: 10,
  name: "ADMIN",
  permissions: ["user:read", "user:write"],
};

describe("httpAdminGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("getUser", () => {
    it("fetches /axonia/admin/users/{userId} and parses the result", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse(adminUser));

      // when
      const actual = await httpAdminGateway.getUser("u1");

      // then
      expect(actual.email).toBe("alice@example.com");
      const [url] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/admin/users/u1");
    });
  });

  describe("listRoles", () => {
    it("fetches /axonia/roles and parses the result", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([role]));

      // when
      const actual = await httpAdminGateway.listRoles();

      // then
      expect(actual[0]?.name).toBe("ADMIN");
      const [url] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/roles");
    });
  });

  describe("roleAssignees", () => {
    it("fetches /axonia/roles/{roleId}/assignees and parses the result", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([roleBinding]));

      // when
      const actual = await httpAdminGateway.roleAssignees(10);

      // then
      expect(actual[0]?.subjectId).toBe("u1");
      const [url] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/roles/10/assignees");
    });
  });

  describe("grantRole", () => {
    it("POSTs to /axonia/role-bindings and parses the result", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse(roleBinding, 201));
      const body = {
        subjectType: "USER",
        subjectId: "u1",
        roleId: 10,
      };

      // when
      const actual = await httpAdminGateway.grantRole(body);

      // then
      expect(actual.id).toBe(1);
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/role-bindings");
      expect(methodOf(init)).toBe("POST");
      const sent = JSON.parse(bodyOf(init)) as Record<string, unknown>;
      expect(sent).toEqual({
        subjectType: "USER",
        subjectId: "u1",
        roleId: 10,
      });
    });
  });

  describe("revokeRole", () => {
    it("DELETEs /axonia/role-bindings/{id}", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpAdminGateway.revokeRole(1);

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/role-bindings/1");
      expect(methodOf(init)).toBe("DELETE");
    });
  });

  describe("listIdpGroups", () => {
    it("fetches /axonia/admin/idp-groups and parses the result", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ source: "SCIM", groups: [idpGroup] }),
      );

      // when
      const actual = await httpAdminGateway.listIdpGroups();

      // then
      expect(actual.source).toBe("SCIM");
      expect(actual.groups[0]?.groupId).toBe("grp-1");
      expect(actual.groups[0]?.bindings[0]?.subjectLabel).toBe("Engineering");
      const [url] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/admin/idp-groups");
    });
  });

  describe("bindGroup", () => {
    it("POSTs to /axonia/role-bindings with GROUP subject body", async () => {
      // given
      const groupBinding = {
        id: 3,
        subjectType: "GROUP",
        subjectId: "grp-1",
        roleId: 10,
        subjectLabel: "Engineering",
      };
      fetchSpy.mockResolvedValue(jsonResponse(groupBinding, 201));
      const body = {
        groupId: "grp-1",
        displayName: "Engineering",
        roleId: 10,
      };

      // when
      const actual = await httpAdminGateway.bindGroup(body);

      // then
      expect(actual.subjectType).toBe("GROUP");
      expect(actual.subjectId).toBe("grp-1");
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/role-bindings");
      expect(methodOf(init)).toBe("POST");
      const sent = JSON.parse(bodyOf(init)) as Record<string, unknown>;
      expect(sent["subjectType"]).toBe("GROUP");
      expect(sent["subjectId"]).toBe("grp-1");
      expect(sent["subjectLabel"]).toBe("Engineering");
    });
  });

  describe("unbindGroup", () => {
    it("DELETEs /axonia/role-bindings/{id}", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpAdminGateway.unbindGroup(3);

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/role-bindings/3");
      expect(methodOf(init)).toBe("DELETE");
    });
  });
});
