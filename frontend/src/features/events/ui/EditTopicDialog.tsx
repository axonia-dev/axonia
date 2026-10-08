import { Dialog } from "@/components/ui/Dialog";
import type { Topic } from "@/features/events/domain/topic";
import { ProposeTopicForm } from "@/features/events/ui/ProposeTopicForm";

interface Props {
  slug: string;
  topic: Topic;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function EditTopicDialog({
  slug,
  topic,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title="Edit topic">
      <ProposeTopicForm
        slug={slug}
        topicId={topic.id}
        initialKind={topic.kind}
        initialTitle={topic.title}
        initialDescription={topic.description}
        initialTags={topic.tags}
        onProposed={() => onOpenChange(false)}
      />
    </Dialog>
  );
}
