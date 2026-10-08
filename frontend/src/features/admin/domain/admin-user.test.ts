import { describe, expect, it } from "vitest";

import { adminUserPageSchema, adminUserSchema } from "./admin-user";

const pageOf = (content: unknown[]) => ({
  content,
  page: {
    size: 20,
    number: 0,
    totalElements: content.length,
    totalPages: 1,
  },
});

describe("adminUserSchema", () => {
  it("accepts a SCIM-provisioned user with a null userId (not yet signed in)", () => {
    const parsed = adminUserSchema.parse({
      userId: null,
      displayName: "Pending Pat",
      email: "pat@example.com",
      effectiveRoles: [],
      memberStatus: "PRESENT",
    });

    expect(parsed.userId).toBeNull();
  });

  it("does not let one null-userId user poison the whole list", () => {
    const parsed = adminUserPageSchema.parse(
      pageOf([
        {
          userId: "u1",
          displayName: "Alice",
          email: "alice@example.com",
          effectiveRoles: [],
          memberStatus: "PRESENT",
        },
        {
          userId: null,
          displayName: "Pending Pat",
          email: "pat@example.com",
          effectiveRoles: [],
          memberStatus: "PRESENT",
        },
      ]),
    );

    expect(parsed.content).toHaveLength(2);
    expect(parsed.content[1]?.userId).toBeNull();
  });

  it("keeps the roster total, which a page of the list cannot carry", () => {
    const parsed = adminUserPageSchema.parse({
      content: [
        {
          userId: "u1",
          displayName: "Alice",
          email: "alice@example.com",
          effectiveRoles: [],
          memberStatus: "PRESENT",
        },
      ],
      page: { size: 20, number: 0, totalElements: 42, totalPages: 3 },
    });

    expect(parsed.content).toHaveLength(1);
    expect(parsed.totalElements).toBe(42);
  });

  it("rejects a user without memberStatus", () => {
    // A departed member who lost their status marker would render as PRESENT everywhere
    // this list is shown - the roster's whole reason for existing.
    expect(() =>
      adminUserSchema.parse({
        userId: "u1",
        displayName: "Alice",
        email: "alice@example.com",
        effectiveRoles: [],
      }),
    ).toThrow();
  });

  it("carries a deactivated member through the admin roster", () => {
    const parsed = adminUserSchema.parse({
      userId: "u1",
      displayName: "Alice",
      email: "alice@example.com",
      effectiveRoles: [],
      memberStatus: "DEACTIVATED",
    });

    expect(parsed.memberStatus).toBe("DEACTIVATED");
  });
});
