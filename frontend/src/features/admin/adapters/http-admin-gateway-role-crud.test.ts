import { describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import * as apiClient from "@/lib/api-client";

describe("httpAdminGateway role CRUD", () => {
  it("createRole posts the body and parses the role", async () => {
    const role = {
      id: 9,
      name: "Curator",
      description: null,
      permissions: ["library_type:read"],
    };
    const spy = vi.spyOn(apiClient, "apiFetch").mockResolvedValue(role);

    const result = await httpAdminGateway.createRole({
      name: "Curator",
      description: null,
      permissions: ["library_type:read"],
    });

    expect(spy).toHaveBeenCalledWith("/axonia/roles", {
      method: "POST",
      body: JSON.stringify({
        name: "Curator",
        description: null,
        permissions: ["library_type:read"],
      }),
    });
    expect(result.id).toBe(9);
  });

  it("updateRole puts to /roles/:id", async () => {
    const role = {
      id: 9,
      name: "Curator",
      description: "x",
      permissions: [],
    };
    const spy = vi.spyOn(apiClient, "apiFetch").mockResolvedValue(role);

    await httpAdminGateway.updateRole(9, {
      name: "Curator",
      description: "x",
      permissions: [],
    });

    expect(spy).toHaveBeenCalledWith("/axonia/roles/9", {
      method: "PUT",
      body: JSON.stringify({
        name: "Curator",
        description: "x",
        permissions: [],
      }),
    });
  });

  it("deleteRole deletes /roles/:id", async () => {
    const spy = vi
      .spyOn(apiClient, "apiFetchVoid")
      .mockResolvedValue(undefined);

    await httpAdminGateway.deleteRole(9);

    expect(spy).toHaveBeenCalledWith("/axonia/roles/9", { method: "DELETE" });
  });
});
