import type { User } from "@/features/identity/domain/user";

export function displayNameOf(id: string, users: Record<string, User>): string {
  return users[id]?.displayName ?? "Member";
}

/**
 * The card's byline: who gave the talk, in reading order. `null` for an open slot - the byline
 * row is omitted entirely rather than rendered empty. Built from `.at(-1)` rather than `[0]`
 * indexing so it type-checks under `noUncheckedIndexedAccess` without a non-null assertion.
 */
export function presenterByline(
  userIds: string[],
  users: Record<string, User>,
): string | null {
  if (userIds.length === 0) {
    return null;
  }
  const names = userIds.map((id) => displayNameOf(id, users));
  const last = names.at(-1);
  if (!last) {
    return null;
  }
  if (names.length === 1) {
    return `by ${last}`;
  }
  return `by ${names.slice(0, -1).join(", ")} & ${last}`;
}
