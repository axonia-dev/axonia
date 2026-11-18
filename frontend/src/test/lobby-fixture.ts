import type { Lobby, LobbyItem } from "@/features/community/domain/lobby";

/** An unjoined community with nothing scheduled, nothing shared and one face; a test names what it varies. */
export function lobbyItem(overrides: Partial<LobbyItem> = {}): LobbyItem {
  const id = overrides.id ?? 1;
  return {
    id,
    slug: `community-${id}`,
    name: `Community ${id}`,
    kindId: 1,
    kind: "Guild",
    description: "",
    status: "ACTIVE",
    memberCount: 1,
    mentoringEnabled: false,
    createdAt: "2026-05-31T10:00:00Z",
    updatedAt: "2026-05-31T10:00:00Z",
    role: null,
    nextSession: null,
    nextSessionThisWeek: false,
    latestSession: null,
    joinedLast30Days: 0,
    faces: ["alice"],
    stats: { sessionsHeld: 0, artifactsShared: 0 },
    lately: { artifactTitle: null, topicTitle: null },
    ...overrides,
  };
}

export function lobbyFixture(overrides: Partial<Lobby> = {}): Lobby {
  return { billboard: [], shelves: [], ...overrides };
}
