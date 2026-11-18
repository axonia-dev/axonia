import type { CommunityGateway } from "@/features/community/domain/community-gateway";
import {
  lobbyItemSchema,
  lobbySchema,
  type LobbyShelfKey,
} from "@/features/community/domain/lobby";
import { pageSchema } from "@/features/community/domain/page";
import { apiFetch } from "@/lib/api-client";
import { buildQuery as query } from "@/lib/query";

const lobbyItemPageSchema = pageSchema(lobbyItemSchema);

export const httpLobbyCalls: Pick<
  CommunityGateway,
  "lobby" | "lobbyShelf" | "lobbySearch"
> = {
  async lobby() {
    return lobbySchema.parse(await apiFetch<unknown>("/axonia/lobby"));
  },

  async lobbyShelf(key: LobbyShelfKey, page: number, size: number) {
    const data = await apiFetch<unknown>(
      `/axonia/lobby/shelves/${key}${query({ page, size })}`,
    );
    return lobbyItemPageSchema.parse(data);
  },

  async lobbySearch(q: string, page: number, size: number) {
    const data = await apiFetch<unknown>(
      `/axonia/lobby/search${query({ q, page, size })}`,
    );
    return lobbyItemPageSchema.parse(data);
  },
};
