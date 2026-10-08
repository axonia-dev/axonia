import type { SaveableRef } from "@/features/bookmarks/domain/saved";

export const saveKeys = {
  all: ["save"] as const,
  state: (ref: SaveableRef) =>
    [...saveKeys.all, "state", ref.kind, ref.id] as const,
  mine: () => [...saveKeys.all, "mine"] as const,
};
