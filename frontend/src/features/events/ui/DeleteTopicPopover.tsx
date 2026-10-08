import { ConfirmPopover } from "@/components/patterns/ConfirmPopover";
import { useDeleteTopic } from "@/features/events/api/use-topics";
import type { Topic } from "@/features/events/domain/topic";

interface Props {
  slug: string;
  topic: Topic;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DeleteTopicPopover({
  slug,
  topic,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  const deleteTopic = useDeleteTopic(slug);

  function handleDelete() {
    deleteTopic.mutate(topic.id, { onSuccess: () => onOpenChange(false) });
  }

  return (
    <ConfirmPopover
      label="Confirm delete"
      isOpen={isOpen}
      confirmLabel="Remove"
      isPending={deleteTopic.isPending}
      onConfirm={handleDelete}
      onCancel={() => onOpenChange(false)}
    >
      Remove <strong>{topic.title}</strong> from the backlog?
    </ConfirmPopover>
  );
}
