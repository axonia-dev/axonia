import type { Community } from "@/features/community/domain/community";

/** An active Backend Guild with mentoring off, as the community endpoints answer it; a test names only what it varies. */
export function communityFixture(
  overrides: Partial<Community> = {},
): Community {
  return {
    id: 1,
    slug: "backend-guild",
    name: "Backend Guild",
    kindId: 1,
    kind: "Guild",
    description: "",
    status: "ACTIVE",
    memberCount: 3,
    mentoringEnabled: false,
    createdAt: "2026-05-31T10:00:00Z",
    updatedAt: "2026-05-31T10:00:00Z",
    ...overrides,
  };
}
