import { describe, expect, it } from "vitest";

import { primaryOrgUnit, userSchema } from "@/features/identity/domain/user";

describe("userSchema", () => {
  it("parses a full profile", () => {
    // given
    const raw = {
      id: "11111111-1111-1111-1111-111111111111",
      displayName: "Alice Anderson",
      email: "alice@example.com",
      memberStatus: "PRESENT",
    };

    // when
    const actual = userSchema.parse(raw);

    // then
    expect(actual).toEqual({
      ...raw,
      avatarIsOwn: false,
      timeZoneIsOwn: false,
    });
  });

  it("defaults a missing avatarIsOwn to false rather than true", () => {
    // A missing value must never default to `true`: that would offer a Remove
    // action on an avatar the member never actually set.
    // when
    const actual = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      memberStatus: "PRESENT",
    });

    // then
    expect(actual.avatarIsOwn).toBe(false);
  });

  it("defaults a missing timeZoneIsOwn to false rather than true", () => {
    // Same reason as avatarIsOwn above: `true` would offer an Automatic that clears nothing.
    // when
    const actual = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      memberStatus: "PRESENT",
    });

    // then
    expect(actual.timeZoneIsOwn).toBe(false);
  });

  it("leaves an unmeasured member with no clock rather than defaulting one", () => {
    // Unknown is a state every reader must be able to tell from a measurement.
    // when
    const actual = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      memberStatus: "PRESENT",
    });

    // then
    expect(actual.timeZone).toBeUndefined();
  });

  it("accepts a null email (token carried none)", () => {
    // when
    const actual = userSchema.parse({
      id: "sub",
      displayName: "alice",
      email: null,
      memberStatus: "PRESENT",
    });

    // then
    expect(actual.email).toBeNull();
  });

  it("rejects a profile without a display name", () => {
    // when / then
    expect(() =>
      userSchema.parse({ id: "sub", email: null, memberStatus: "PRESENT" }),
    ).toThrow();
  });

  it("rejects a profile without memberStatus", () => {
    // when / then - a schema that let this through would silently render every
    // departed member as PRESENT instead of failing loudly.
    expect(() =>
      userSchema.parse({ id: "sub", displayName: "Alice", email: null }),
    ).toThrow();
  });

  it("accepts org attributes and avatar", () => {
    const u = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      email: "a@x.com",
      avatarUrl: "https://img/a.png",
      department: "Platform",
      businessUnit: "Acme",
      team: "Payments",
      memberStatus: "PRESENT",
    });
    expect(u.department).toBe("Platform");
    expect(u.avatarUrl).toBe("https://img/a.png");
  });

  it("tolerates missing org attributes", () => {
    const u = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      memberStatus: "PRESENT",
    });
    expect(u.department ?? null).toBeNull();
  });

  it("carries a deactivated member's real name and status", () => {
    const u = userSchema.parse({
      id: "u1",
      displayName: "Alex",
      memberStatus: "DEACTIVATED",
    });
    expect(u.memberStatus).toBe("DEACTIVATED");
    expect(u.displayName).toBe("Alex");
  });

  it("carries an erased member under the backend's neutral label", () => {
    const u = userSchema.parse({
      id: "u1",
      displayName: "Deleted member",
      memberStatus: "ERASED",
    });
    expect(u.memberStatus).toBe("ERASED");
  });
});

describe("primaryOrgUnit", () => {
  it("names the finest unit set", () => {
    // when
    const actual = primaryOrgUnit({
      businessUnit: "Engineering",
      department: "Platform",
      team: "Build & CI",
    });

    // then
    expect(actual).toBe("Build & CI");
  });

  it("falls back past blank units and is null when none is set", () => {
    // when
    const department = primaryOrgUnit({ team: "", department: "Platform" });
    const none = primaryOrgUnit({ team: null });

    // then
    expect(department).toBe("Platform");
    expect(none).toBeNull();
  });
});
