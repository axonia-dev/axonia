import { useState } from "react";

import { TextInput } from "@/components/ui/TextInput";
import { useRecordSlot } from "@/features/events/api/use-slot-presenters";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { PresenterMultiPicker } from "@/features/events/ui/PresenterMultiPicker";
import { DEFAULT_MINUTES } from "@/features/events/ui/slot-dialog-helpers";
import { SlotFormTail } from "@/features/events/ui/SlotFormTail";
import { SlotTagsField } from "@/features/events/ui/SlotTagsField";

interface RecordFormProps {
  slug: string;
  session: Session;
  section: Section;
  onDone: () => void;
}

/**
 * Records a slot in a session opened for editing: correcting or backfilling what actually
 * happened. Unlike AddSlotForm, this never signs up the caller and never offers the backlog -
 * pulling a proposed future topic into a past session is wrong. Presenters are optional and,
 * when given, are specific community members (sent as userIds), never free text - a talk may
 * name several. The talk carries its own topics, which is what makes a backfilled history count
 * towards each presenter's expertise.
 */
export function RecordSlotForm({
  slug,
  session,
  section,
  onDone,
}: Readonly<RecordFormProps>) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES);
  const [presenters, setPresenters] = useState<string[]>([]);
  const [tags, setTags] = useState<string[]>([]);

  const recordSlot = useRecordSlot(slug, session.id);

  const submit = () => {
    recordSlot.mutate(
      {
        sectionId: section.id,
        input: {
          title: title.trim(),
          durationMinutes: minutes,
          notes: description.trim(),
          presenters,
          tags,
        },
      },
      { onSuccess: onDone },
    );
  };

  return (
    <div className="flex flex-col gap-3">
      <TextInput label="Subject" value={title} onChange={setTitle} isRequired />
      <PresenterMultiPicker
        slug={slug}
        value={presenters}
        onChange={setPresenters}
      />
      <TextInput
        label="Description"
        value={description}
        onChange={setDescription}
      />
      <SlotTagsField value={tags} onChange={setTags} />
      <SlotFormTail
        minutes={minutes}
        onMinutesChange={setMinutes}
        section={section}
        onCancel={onDone}
        onSubmit={submit}
        submitLabel="Add"
        submitDisabled={recordSlot.isPending || title.trim() === ""}
        error={recordSlot.error}
      />
    </div>
  );
}
