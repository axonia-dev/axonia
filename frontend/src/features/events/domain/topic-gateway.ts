import type { PageParams } from "@/features/events/domain/events-gateway";
import type {
  SpeakerAssignment,
  Topic,
  TopicKind,
} from "@/features/events/domain/topic";
import type { TopicCounts } from "@/features/events/domain/topic-counts";
import type { Page } from "@/lib/page";

export interface TopicInput {
  kind: TopicKind;
  title: string;
  description?: string;
  tags?: string[];
}

/** An edit never changes the kind: it follows from whether the topic has speakers. */
export type TopicEditInput = Omit<TopicInput, "kind">;

export interface TopicListParams extends PageParams {
  kind?: TopicKind;
  status?: "PROPOSED" | "SCHEDULED" | "DONE";
  /** Read-time lifecycle view; only "PAST" is served server-side. */
  phase?: "PAST";
  /** Free-text search over title/description (used by the Past topics tab). */
  q?: string;
}

/** The topic backlog as its own port: proposing, voting, editing, and who will present a topic.
 *  Composed back into `EventsGateway`, as `SlotGateway` is. */
export interface TopicGateway {
  listTopics(slug: string, params: TopicListParams): Promise<Page<Topic>>;
  getTopicCounts(slug: string): Promise<TopicCounts>;
  proposeTopic(slug: string, input: TopicInput): Promise<Topic>;
  voteTopic(topicId: number): Promise<Topic>;
  unvoteTopic(topicId: number): Promise<void>;
  editTopic(topicId: number, input: TopicEditInput): Promise<Topic>;
  deleteTopic(topicId: number): Promise<void>;
  returnToBacklog(topicId: number): Promise<void>;
  addSelfAsSpeaker(topicId: number): Promise<Topic>;
  addSpeaker(topicId: number, speaker: string): Promise<SpeakerAssignment>;
  withdrawAsSpeaker(topicId: number): Promise<Topic>;
  removeSpeaker(topicId: number, userId: string): Promise<Topic>;
}
