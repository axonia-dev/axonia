import type {
  SavedArtifactView,
  SavedItem,
  SavedSessionView,
} from "@/features/bookmarks/domain/saved";

type SavedCommunity = SavedItem["community"];

function backendGuild(id: number): SavedCommunity {
  return {
    id,
    slug: "backend-guild",
    name: "Backend Guild",
    accentColor: null,
  };
}

const VIEW_DEFAULTS = {
  id: 1,
  title: "Virtual threads",
  description: "",
  tags: [] as string[],
  typeId: 3,
  communityId: 2,
  loveCount: 0,
  savedAt: "2026-06-02T00:00:00Z",
  helped: null as boolean | null,
};

type ViewOverrides = Partial<typeof VIEW_DEFAULTS> & {
  community?: SavedCommunity;
};

export function view(overrides: ViewOverrides = {}): SavedArtifactView {
  const o = { ...VIEW_DEFAULTS, ...overrides };
  return {
    kind: "artifact",
    artifact: {
      id: o.id,
      communityId: o.communityId,
      typeId: o.typeId,
      type: "RFC",
      title: o.title,
      description: o.description,
      url: "https://example.com/x",
      tags: o.tags,
      contributorUserId: "alice",
      loveCount: o.loveCount,
      provenance: null,
      status: "ACTIVE",
      createdAt: "2026-06-01T10:00:00Z",
      updatedAt: "2026-06-01T10:00:00Z",
    },
    community:
      overrides.community === undefined
        ? backendGuild(o.communityId)
        : overrides.community,
    savedAt: o.savedAt,
    helped: o.helped,
  };
}

const SESSION_DEFAULTS = {
  id: 10,
  title: "Kafka office hours" as string | null,
  status: "SCHEDULED" as SavedSessionView["session"]["status"],
  communityId: 2,
  savedAt: "2026-06-02T00:00:00Z",
};

type SessionOverrides = Partial<typeof SESSION_DEFAULTS> & {
  community?: SavedCommunity;
};

export function sessionView(
  overrides: SessionOverrides = {},
): SavedSessionView {
  const o = { ...SESSION_DEFAULTS, ...overrides };
  return {
    kind: "session",
    session: {
      id: o.id,
      communityId: o.communityId,
      title: o.title,
      status: o.status,
      scheduledAt: "2026-06-05T10:00:00Z",
      occurrenceDate: null,
      facilitatorUserId: null,
      slotCount: 0,
      recordingUrl: null,
    },
    community:
      overrides.community === undefined
        ? backendGuild(o.communityId)
        : overrides.community,
    savedAt: o.savedAt,
  };
}
