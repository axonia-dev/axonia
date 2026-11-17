import { type SyntheticEvent, useState } from "react";

import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import { useJournalWrite } from "@/features/mentoring/api/use-mentoring";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

const MAX_LABEL_LENGTH = 80;

/** A new topic goes last in To do. The field empties at once, so the next topic can be typed while this one saves. */
export function AddTopic({ pairingId }: Readonly<{ pairingId: number }>) {
  const { addTopic } = useJournalWrite(pairingId);
  const [label, setLabel] = useState("");
  const trimmed = label.trim();

  function submit(event: SyntheticEvent) {
    event.preventDefault();
    if (trimmed === "" || addTopic.isPending) return;
    setLabel("");
    addTopic.mutate(
      { label: trimmed, area: null },
      {
        onError: (error) => {
          setLabel((typed) => (typed === "" ? trimmed : typed));
          onFailure(error);
        },
      },
    );
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <TextInput
        label="New topic"
        value={label}
        onChange={setLabel}
        maxLength={MAX_LABEL_LENGTH}
        placeholder="e.g. Giving feedback"
      />
      <Button
        type="submit"
        variant="secondary"
        size="sm"
        className="self-start"
        isDisabled={trimmed === "" || addTopic.isPending}
      >
        Add topic
      </Button>
    </form>
  );
}
