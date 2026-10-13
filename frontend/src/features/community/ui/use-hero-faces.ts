import type { FacepileMember } from "@/components/patterns/MemberFacepile";
import { useMembers } from "@/features/community/api/use-membership";
import { useUsers } from "@/features/identity/api/use-users";
import { userHue } from "@/lib/user-hue";

export const HERO_FACES_SHOWN = 5;

/** The community's newest people, as a capped facepile. Reads `{ page: 0, size: 100 }`, the same
 *  arguments `NewMembers` holds open on the community home, so the request is shared; then sorts
 *  by `joinedAt` descending and caps it, since the backend applies no ordering of its own. */
export function useHeroFaces(slug: string): FacepileMember[] {
  const members = useMembers(slug, { page: 0, size: 100 });
  const rows = (members.data?.content ?? [])
    .slice()
    .sort((a, b) => Date.parse(b.joinedAt) - Date.parse(a.joinedAt))
    .slice(0, HERO_FACES_SHOWN);
  const users = useUsers(rows.map((member) => member.userId));

  return rows.map((member) => ({
    id: member.userId,
    name: users.data?.[member.userId]?.displayName ?? "Someone",
    hue: userHue(member.userId),
    src: users.data?.[member.userId]?.avatarUrl ?? null,
  }));
}
