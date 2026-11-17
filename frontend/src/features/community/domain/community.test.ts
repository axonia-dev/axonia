import { describe, expect, it } from "vitest";

import {
  canEdit,
  type Community,
  communitySchema,
  isArchived,
} from "@/features/community/domain/community";

const active: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE",
  memberCount: 3,
  mentoringEnabled: false,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

describe("communitySchema", () => {
  it("parses a valid community", () => {
    // given the `active` fixture above

    // when
    const actual = communitySchema.parse(active);

    // then
    expect(actual).toEqual(active);
  });

  it("reads mentoringEnabled as given", () => {
    // when
    const actual = communitySchema.parse({ ...active, mentoringEnabled: true });

    // then
    expect(actual.mentoringEnabled).toBe(true);
  });

  it("rejects a community that does not say whether mentoring is on", () => {
    // given
    const silent: Partial<Community> = { ...active };
    delete silent.mentoringEnabled;

    // when
    const actual = () => communitySchema.parse(silent);

    // then
    expect(actual).toThrow();
  });

  it("rejects an unknown status", () => {
    // given
    const invalid = { ...active, status: "DRAFT" };

    // when / then
    expect(() => communitySchema.parse(invalid)).toThrow();
  });
});

describe("isArchived / canEdit", () => {
  it("reflects an active community", () => {
    // given the `active` fixture above

    // when / then
    expect(isArchived(active)).toBe(false);
    expect(canEdit(active)).toBe(true);
  });

  it("reflects an archived community", () => {
    // given
    const archived = { ...active, status: "ARCHIVED" as const };

    // when / then
    expect(isArchived(archived)).toBe(true);
    expect(canEdit(archived)).toBe(false);
  });
});
