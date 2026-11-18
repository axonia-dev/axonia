import type { FacepileMember } from "@/components/patterns/MemberFacepile";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { useUsers } from "@/features/identity/api/use-users";
import { userHue } from "@/lib/user-hue";

export type LobbyFaces = (item: LobbyItem, max: number) => FacepileMember[];

/**
 * The faces of every item at once, through one directory read for the union of their ids, so a
 * shelf of cards asks once rather than once per card. An item outside `items` is answered by
 * `fallback`, which lets a shelf resolve only the cards the lobby did not bring.
 */
export function useLobbyFaces(
  items: readonly LobbyItem[],
  fallback?: LobbyFaces,
): LobbyFaces {
  const ids = [...new Set(items.flatMap((item) => item.faces))];
  const users = useUsers(ids);
  const own = new Set(items.map((item) => item.id));
  const directory = new Map(Object.entries(users.data ?? {}));

  return (item, max) => {
    if (fallback !== undefined && !own.has(item.id)) {
      return fallback(item, max);
    }
    return item.faces.slice(0, max).map((id) => ({
      id,
      name: directory.get(id)?.displayName ?? "Someone",
      hue: userHue(id),
      src: directory.get(id)?.avatarUrl ?? null,
    }));
  };
}
