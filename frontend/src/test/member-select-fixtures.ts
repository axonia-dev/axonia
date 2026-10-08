import { vi } from "vitest";

import type { Membership } from "@/features/community/domain/membership";
import type { Page } from "@/lib/page";

/** A member as a picker's caller names them: enough to seed the roster and the identity
 *  directory `MemberSelectField` joins against. */
export interface StubMember {
  userId: string;
  displayName: string;
}

function membershipOf(member: StubMember, index: number): Membership {
  return {
    id: index + 1,
    communityId: 1,
    userId: member.userId,
    role: "MEMBER",
    joinedAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  };
}

function rosterPageOf(members: StubMember[]): Page<Membership> {
  return {
    content: members.map(membershipOf),
    totalElements: members.length,
    totalPages: 1,
    number: 0,
    size: 25,
  };
}

function directoryOf(members: StubMember[]) {
  return Object.fromEntries(
    members.map(({ userId, displayName }) => [
      userId,
      { id: userId, displayName, email: null },
    ]),
  );
}

interface MemberSelectHooks {
  useRosterByName: ReturnType<typeof vi.fn>;
  useMemberSearch: ReturnType<typeof vi.fn>;
  useUsers: ReturnType<typeof vi.fn>;
}

/**
 * Stubs the three hooks `MemberSelectField` calls, so any form that embeds it opens on the
 * given roster without reaching the network. Callers still own their own `vi.mock` of each
 * module - vitest hoists it per file - this only fills in the return values.
 */
export function stubMemberSelectField(
  hooks: Readonly<MemberSelectHooks>,
  members: StubMember[] = [],
) {
  hooks.useRosterByName.mockReturnValue({
    data: { pages: [rosterPageOf(members)] },
    hasNextPage: false,
    fetchNextPage: vi.fn(),
  });
  hooks.useMemberSearch.mockReturnValue({
    data: undefined,
    isFetching: false,
  });
  hooks.useUsers.mockReturnValue({ data: directoryOf(members) });
}
