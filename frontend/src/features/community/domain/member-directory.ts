import type { Membership } from "@/features/community/domain/membership";
import type { User } from "@/features/identity/domain/user";

export type DirectorySegment = "all" | "organizers" | "members";

export interface RoleCounts {
  all: number;
  organizers: number;
  members: number;
}

export function countByRole(members: readonly Membership[]): RoleCounts {
  const organizers = members.filter((m) => m.role === "ORGANIZER").length;
  return {
    all: members.length,
    organizers,
    members: members.length - organizers,
  };
}

function matchesSegment(
  member: Membership,
  segment: DirectorySegment,
): boolean {
  if (segment === "organizers") return member.role === "ORGANIZER";
  if (segment === "members") return member.role === "MEMBER";
  return true;
}

export function filterMembers(
  members: readonly Membership[],
  users: Record<string, User>,
  query: string,
  segment: DirectorySegment,
): Membership[] {
  const needle = query.trim().toLowerCase();
  return members.filter((member) => {
    if (!matchesSegment(member, segment)) return false;
    if (needle === "") return true;
    const user = users[member.userId];
    const haystack = [
      user?.displayName ?? member.userId,
      user?.team ?? "",
      user?.department ?? "",
    ]
      .join(" ")
      .toLowerCase();
    return haystack.includes(needle);
  });
}

const JOINED_FORMAT = new Intl.DateTimeFormat("en", {
  month: "short",
  year: "numeric",
});

export function formatJoinedMonth(iso: string): string {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return "";
  return JOINED_FORMAT.format(new Date(ms));
}
