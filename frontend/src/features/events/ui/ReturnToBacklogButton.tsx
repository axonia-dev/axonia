import { Undo2 } from "lucide-react";

import { IconButton } from "@/components/ui/IconButton";
import { useReturnToBacklog } from "@/features/events/api/use-backlog-pull";
import { eventsErrorMessage } from "@/features/events/events-errors";

export function ReturnToBacklogButton({
  slug,
  topicId,
}: Readonly<{ slug: string; topicId: number }>) {
  const mutation = useReturnToBacklog(slug);

  return (
    <div className="flex flex-col items-end gap-1">
      <IconButton
        label="Move to backlog"
        isDisabled={mutation.isPending}
        onPress={() => mutation.mutate(topicId)}
      >
        <Undo2 />
      </IconButton>
      {mutation.error != null && (
        <p role="alert" className="text-danger text-sm">
          {eventsErrorMessage(mutation.error)}
        </p>
      )}
    </div>
  );
}
