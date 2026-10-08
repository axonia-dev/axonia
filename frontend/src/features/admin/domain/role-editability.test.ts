import { describe, expect, it } from "vitest";

import { isEditable } from "@/features/admin/domain/role-editability";

function role(system: boolean) {
  return {
    id: 1,
    name: "Support Lead",
    description: null,
    system,
    permissions: [],
  };
}

describe("isEditable", () => {
  it("is true for a custom role and a writer", () => {
    expect(isEditable(role(false), true)).toBe(true);
  });

  it("is false for a system role even when the caller can write", () => {
    expect(isEditable(role(true), true)).toBe(false);
  });

  it("is false for a reader even on a custom role", () => {
    expect(isEditable(role(false), false)).toBe(false);
  });

  it("is false for a reader on a system role", () => {
    expect(isEditable(role(true), false)).toBe(false);
  });
});
