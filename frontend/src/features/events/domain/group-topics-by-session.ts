import type { Topic } from "@/features/events/domain/topic";

type ScheduledTopic = Topic & {
  scheduledFor: NonNullable<Topic["scheduledFor"]>;
};

export interface SessionGroup {
  sessionId: number;
  scheduledAt: string;
  scheduleLabel: string;
  topics: ScheduledTopic[];
}

/**
 * Upcoming topics bucketed by the session that will host them, soonest
 * session first. The first group is the community's next session.
 */
export function groupTopicsBySession(
  topics: Topic[],
  now: Date,
): SessionGroup[] {
  const upcoming = topics.filter(
    (t): t is ScheduledTopic =>
      t.scheduledFor !== null &&
      new Date(t.scheduledFor.scheduledAt).getTime() >= now.getTime(),
  );
  const groups = new Map<number, SessionGroup>();
  for (const topic of upcoming) {
    const { sessionId, scheduledAt, scheduleLabel } = topic.scheduledFor;
    const group = groups.get(sessionId) ?? {
      sessionId,
      scheduledAt,
      scheduleLabel,
      topics: [],
    };
    group.topics.push(topic);
    groups.set(sessionId, group);
  }
  return [...groups.values()].sort(
    (a, b) =>
      new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime(),
  );
}
