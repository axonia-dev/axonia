import type {
  PresenterAssignment,
  RecordedSlot,
} from "@/features/events/domain/presenter";
import type { Session } from "@/features/events/domain/session";

export interface SlotInput {
  title: string;
  topicId?: number;
  durationMinutes: number;
  notes?: string;
  /**
   * The talk's own subject, for a slot that was never proposed. A slot has a topic or its own
   * tags, never both: sending a topicId and tags together is refused (SLOT_TOPIC_AND_TAGS).
   */
  tags?: string[];
}

export interface RecordSlotInput {
  title: string;
  durationMinutes: number;
  notes?: string;
  /** Display names, emails, or user ids. Each is resolved against the community's members. */
  presenters: string[];
  /** The talk's own subject: a recorded slot never carries a topic. */
  tags?: string[];
}

export interface EditSlotInput {
  title: string;
  topicId?: number;
  durationMinutes: number;
  notes?: string;
  /**
   * Omitted keeps the slot's current subject; an explicit empty list clears it. Never sent
   * alongside a topicId, which the backend refuses (SLOT_TOPIC_AND_TAGS).
   */
  tags?: string[];
}

export interface QueueTopicInput {
  topicId: number;
  durationMinutes: number;
  title?: string;
  notes?: string;
}

/**
 * What a session's agenda is made of, as its own port: the slots inside a section, who presents
 * them, and the order they run in.
 *
 * Split out of `EventsGateway` along a seam the adapter layer had already drawn for itself -
 * `http-slot-endpoints.ts` has implemented exactly these calls as a `Pick` for as long as it has
 * existed, while the port they came from kept declaring them beside series, schedule and
 * release concerns. The interface is composed back into `EventsGateway`, so nothing that depends
 * on the whole gateway changes; only the file a reader lands in when asking what a slot is.
 */
export interface SlotGateway {
  addSlot(
    sessionId: number,
    sectionId: number,
    input: SlotInput,
  ): Promise<Session>;
  queueTopic(
    sessionId: number,
    sectionId: number,
    input: QueueTopicInput,
  ): Promise<Session>;
  reorderSlots(
    sessionId: number,
    sectionId: number,
    slotIds: number[],
  ): Promise<Session>;
  presentSlot(
    sessionId: number,
    sectionId: number,
    slotId: number,
  ): Promise<Session>;
  withdrawSlot(
    sessionId: number,
    sectionId: number,
    slotId: number,
  ): Promise<Session>;
  editSlot(
    sessionId: number,
    sectionId: number,
    slotId: number,
    input: EditSlotInput,
  ): Promise<Session>;
  removeSlot(
    sessionId: number,
    sectionId: number,
    slotId: number,
  ): Promise<Session>;
  recordSlot(
    sessionId: number,
    sectionId: number,
    input: RecordSlotInput,
  ): Promise<RecordedSlot>;
  addPresenter(
    sessionId: number,
    sectionId: number,
    slotId: number,
    presenter: string,
  ): Promise<PresenterAssignment>;
  removePresenter(
    sessionId: number,
    sectionId: number,
    slotId: number,
    userId: string,
  ): Promise<Session>;
}
