import { type SyntheticEvent, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { Select } from "@/components/ui/Select";
import { TagInput } from "@/components/ui/TagInput";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import {
  useEditTopic,
  useProposeTopic,
} from "@/features/events/api/use-topics";
import type { TopicInput } from "@/features/events/domain/events-gateway";
import type { TopicKind } from "@/features/events/domain/topic";

interface Props {
  slug: string;
  onProposed?: () => void;
  // Edit mode - the initial* props are provided together
  topicId?: number;
  initialKind?: TopicKind;
  initialTitle?: string;
  initialDescription?: string;
  initialTags?: string[];
}

export function ProposeTopicForm({
  slug,
  onProposed,
  topicId,
  initialKind,
  initialTitle,
  initialDescription,
  initialTags,
}: Readonly<Props>) {
  const isEditMode = topicId !== undefined;
  const [kind, setKind] = useState<TopicKind>(initialKind ?? "REQUEST");
  const [title, setTitle] = useState(initialTitle ?? "");
  const [description, setDescription] = useState(initialDescription ?? "");
  const [tags, setTags] = useState<string[]>(initialTags ?? []);

  const propose = useProposeTopic(slug);
  const edit = useEditTopic(slug);

  const disabled = title.trim() === "";
  const isPending = isEditMode ? edit.isPending : propose.isPending;
  const isError = isEditMode ? edit.isError : propose.isError;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    const input: TopicInput = { kind, title, description, tags };
    if (topicId !== undefined) {
      edit.mutate({ topicId, input }, { onSuccess: () => onProposed?.() });
    } else {
      propose.mutate(input, {
        onSuccess: () => {
          setTitle("");
          setDescription("");
          setTags([]);
          onProposed?.();
        },
      });
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex max-w-md flex-col gap-4">
      <Select label="Kind" value={kind} options={KINDS} onChange={setKind} />
      <TextInput label="Title" value={title} onChange={setTitle} isRequired />
      <TextArea
        label="Description"
        value={description}
        onChange={setDescription}
        rows={3}
      />
      <TagInput
        label="Tags"
        value={tags}
        onChange={setTags}
        placeholder="e.g. typescript"
      />
      <FormError error={isError} />
      <DialogFooter>
        <Button type="submit" isDisabled={disabled || isPending}>
          {isEditMode ? "Save" : "Propose"}
        </Button>
      </DialogFooter>
    </form>
  );
}

const KINDS: { id: TopicKind; label: string }[] = [
  { id: "REQUEST", label: "Request" },
  { id: "OFFER", label: "Offer" },
];
