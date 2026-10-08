import type {
  ActionItemListParams,
  PageParams,
  PastSessionParams,
  ReleaseMessageType,
  TopicListParams,
} from "@/features/events/domain/events-gateway";

export const sectionTypeKeys = {
  all: ["events", "section-types"] as const,
  list: () => [...sectionTypeKeys.all] as const,
};

export const seriesKeys = {
  all: ["events", "series"] as const,
  community: (slug: string) => [...seriesKeys.all, slug] as const,
};

export const sessionKeys = {
  all: ["events", "sessions"] as const,
  community: (slug: string) => [...sessionKeys.all, slug] as const,
  upcoming: (slug: string, params: PageParams) =>
    [...sessionKeys.community(slug), "upcoming", params] as const,
  past: (slug: string, params: PastSessionParams) =>
    [...sessionKeys.community(slug), "past", params] as const,
  /** Null is the cache slot of an occurrence with no row yet, which is a slot nothing ever fills:
   *  a projected ghost is read from the schedule, never from here. */
  detail: (sessionId: number | null) =>
    [...sessionKeys.all, "detail", sessionId] as const,
  attendance: (sessionId: number) =>
    [...sessionKeys.all, "attendance", sessionId] as const,
  // The message type is part of the key, not just the request: a session's reminder and
  // its wrap-up are two different renders of the same session, so a key naming only the
  // session would serve one dialog the other's cached HTML.
  messagePreview: (sessionId: number, messageType: ReleaseMessageType) =>
    [...sessionKeys.all, "message-preview", sessionId, messageType] as const,
};

export const memberZoneKeys = {
  all: ["events", "member-zones"] as const,
  community: (slug: string) => [...memberZoneKeys.all, slug] as const,
};

export const scheduleKeys = {
  all: ["events", "schedule"] as const,
  community: (slug: string, from: string, to: string) =>
    [...scheduleKeys.all, slug, from, to] as const,
  activity: (slug: string) => [...scheduleKeys.all, slug, "activity"] as const,
};

export const calendarSubscriptionKeys = {
  all: ["events", "calendar-subscription"] as const,
  community: (slug: string) => [...calendarSubscriptionKeys.all, slug] as const,
};

export const topicKeys = {
  all: ["events", "topics"] as const,
  community: (slug: string) => [...topicKeys.all, slug] as const,
  list: (slug: string, params: TopicListParams) =>
    [...topicKeys.community(slug), params] as const,
  counts: (slug: string) => [...topicKeys.community(slug), "counts"] as const,
};

export const actionItemKeys = {
  all: ["events", "action-items"] as const,
  community: (slug: string) => [...actionItemKeys.all, slug] as const,
  list: (slug: string, params: ActionItemListParams) =>
    [...actionItemKeys.community(slug), params] as const,
};

// Stable mutation keys so a topic's in-flight vote/unvote can be tracked from
// the global mutation cache (via useIsMutating) rather than a per-row observer
// — the row is torn down and re-subscribed when the backlog reorders, which
// strands an observer's local isPending. The cache is the single source of truth.
export const topicMutationKeys = {
  vote: ["events", "topics", "vote"] as const,
  unvote: ["events", "topics", "unvote"] as const,
};
