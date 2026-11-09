import { currentUserId } from "@/auth/current-user";
import {
  useAddSelfAsSpeaker,
  useAddSpeaker,
  useRemoveSpeaker,
  useWithdrawAsSpeaker,
} from "@/features/events/api/use-topic-speakers";
import type { Topic } from "@/features/events/domain/topic";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { PeoplePickerPopover } from "@/features/events/ui/PeoplePickerPopover";
import {
  type FaceProps,
  peopleAction,
  PeopleStack,
} from "@/features/events/ui/PeopleStack";
import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import type { User } from "@/features/identity/domain/user";

interface Props {
  slug: string;
  topic: Topic;
  users: Record<string, User>;
  /** False off the backlog: a scheduled or past topic's speakers are its slot's presenters. */
  interactive: boolean;
}

function SpeakerPicker({
  slug,
  topic,
  onClose,
}: Readonly<{ slug: string; topic: Topic; onClose: () => void }>) {
  const addSelf = useAddSelfAsSpeaker(slug, topic.id);
  const addSpeaker = useAddSpeaker(slug, topic.id);

  async function pickSelf() {
    await addSelf.mutateAsync();
  }

  async function pickOther(userId: string) {
    const result = await addSpeaker.mutateAsync(userId);
    return result.assigned;
  }

  const refusal = addSelf.isError ? addSelf.error : addSpeaker.error;

  return (
    <PeoplePickerPopover
      slug={slug}
      label="Add speaker"
      excludeUserIds={topic.speakerUserIds}
      alreadyIn={topic.speakerUserIds.includes(currentUserId() ?? "")}
      canPickOthers={topic.canManageSpeakers}
      selfPending={addSelf.isPending}
      otherPending={addSpeaker.isPending}
      error={refusal ? eventsErrorMessage(refusal) : null}
      onPickSelf={pickSelf}
      onPickOther={pickOther}
      onClose={onClose}
    />
  );
}

const INERT = {
  mutate: () => undefined,
  isPending: false,
  isSuccess: false,
  error: null,
};

function renderFace(_userId: string, face: FaceProps) {
  return <PresenterHoverCard {...face} surface="raised" />;
}

function ManagedSpeakerStack({
  slug,
  topic,
  users,
}: Readonly<Omit<Props, "interactive">>) {
  const withdraw = useWithdrawAsSpeaker(slug, topic.id);
  const remove = useRemoveSpeaker(slug, topic.id);
  const isSpeaker = topic.speakerUserIds.includes(currentUserId() ?? "");

  return (
    <PeopleStack
      userIds={topic.speakerUserIds}
      users={users}
      noun="speaker"
      canManageOthers={topic.canManageSpeakers}
      interactive
      canAdd={!isSpeaker || topic.canManageSpeakers}
      addLabel={`Add speaker to ${topic.title}`}
      renderFace={renderFace}
      withdraw={peopleAction(withdraw, () => withdraw.mutate())}
      remove={peopleAction(remove, (userId: string) => remove.mutate(userId))}
      renderPicker={(onClose) => (
        <SpeakerPicker slug={slug} topic={topic} onClose={onClose} />
      )}
    />
  );
}

/** Who will present a topic, drawn and managed exactly like a slot's presenters. Read-only, it
 *  holds no mutations, so a long scheduled or past list observes none. */
export function TopicSpeakerStack({
  slug,
  topic,
  users,
  interactive,
}: Readonly<Props>) {
  if (interactive) {
    return <ManagedSpeakerStack slug={slug} topic={topic} users={users} />;
  }
  return (
    <PeopleStack
      userIds={topic.speakerUserIds}
      users={users}
      noun="speaker"
      canManageOthers={false}
      interactive={false}
      renderFace={renderFace}
      withdraw={INERT}
      remove={INERT}
      renderPicker={() => null}
    />
  );
}
