import { useState } from "react";

import { TextInput } from "@/components/ui/TextInput";
import { useEditSlot } from "@/features/events/api/use-slot-sign-up";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import {
  anyPending,
  firstError,
} from "@/features/events/ui/slot-dialog-helpers";
import { SlotFormTail } from "@/features/events/ui/SlotFormTail";
import { SlotTagsField } from "@/features/events/ui/SlotTagsField";

interface EditFormProps {
  slug: string;
  session: Session;
  section: Section;
  slot: Slot;
  onDone: () => void;
}

/**
 * A slot filled from the backlog takes its subject from its topic, so it is offered no topics
 * field. A slot that carries no topic owns its subject: its tags are round-tripped through the
 * form, so editing the title cannot silently delete them (an omitted `tags` means "keep", and an
 * explicit empty list means "clear").
 */
export function EditSlotForm({
  slug,
  session,
  section,
  slot,
  onDone,
}: Readonly<EditFormProps>) {
  const [title, setTitle] = useState(slot.title ?? "");
  const [minutes, setMinutes] = useState(slot.durationMinutes);
  const [description, setDescription] = useState(slot.notes ?? "");
  const [tags, setTags] = useState<string[]>(slot.tags);

  const edit = useEditSlot(slug, session.id, section.id, slot.id);

  const busy = anyPending(edit);
  const error = firstError(edit);
  const fromBacklog = slot.topicId !== null;

  const save = () =>
    edit.mutate(
      {
        title: title.trim(),
        durationMinutes: minutes,
        notes: description.trim(),
        ...(slot.topicId !== null ? { topicId: slot.topicId } : { tags }),
      },
      { onSuccess: onDone },
    );

  return (
    <div className="flex flex-col gap-3">
      <TextInput label="Title" value={title} onChange={setTitle} isRequired />
      <TextInput
        label="Description"
        value={description}
        onChange={setDescription}
      />
      {!fromBacklog && <SlotTagsField value={tags} onChange={setTags} />}
      <SlotFormTail
        minutes={minutes}
        onMinutesChange={setMinutes}
        section={section}
        excludeMinutes={slot.durationMinutes}
        onCancel={onDone}
        onSubmit={save}
        submitLabel="Save"
        submitDisabled={busy || title.trim() === ""}
        error={error}
      />
    </div>
  );
}
