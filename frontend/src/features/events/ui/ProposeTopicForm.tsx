import { type ReactNode, type SyntheticEvent, useState } from "react";

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
import type { TopicKind } from "@/features/events/domain/topic";
import type { TopicEditInput } from "@/features/events/domain/topic-gateway";

interface Props {
  slug: string;
  onProposed?: () => void;
  // Edit mode - the initial* props are provided together. The kind is not among them: it
  // follows from whether the topic has speakers.
  topicId?: number;
  initialTitle?: string;
  initialDescription?: string;
  initialTags?: string[];
}

const KINDS: { id: TopicKind; label: string }[] = [
  { id: "REQUEST", label: "Request" },
  { id: "OFFER", label: "Offer" },
];

interface InitialFields {
  title?: string | undefined;
  description?: string | undefined;
  tags?: string[] | undefined;
}

function useTopicFields(initial: InitialFields) {
  const [title, setTitle] = useState(initial.title ?? "");
  const [description, setDescription] = useState(initial.description ?? "");
  const [tags, setTags] = useState<string[]>(initial.tags ?? []);
  return {
    input: { title, description, tags } satisfies TopicEditInput,
    setTitle,
    setDescription,
    setTags,
    reset: () => {
      setTitle("");
      setDescription("");
      setTags([]);
    },
  };
}

type TopicFields = ReturnType<typeof useTopicFields>;

interface TopicFormProps {
  fields: TopicFields;
  kindField?: ReactNode;
  isPending: boolean;
  isError: boolean;
  submitLabel: string;
  onSubmit: () => void;
}

function TopicForm({
  fields,
  kindField,
  isPending,
  isError,
  submitLabel,
  onSubmit,
}: Readonly<TopicFormProps>) {
  const { input } = fields;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <form onSubmit={handleSubmit} className="flex max-w-md flex-col gap-4">
      {kindField}
      <TextInput
        label="Title"
        value={input.title}
        onChange={fields.setTitle}
        isRequired
      />
      <TextArea
        label="Description"
        value={input.description}
        onChange={fields.setDescription}
        rows={3}
      />
      <TagInput
        label="Tags"
        value={input.tags}
        onChange={fields.setTags}
        placeholder="e.g. typescript"
      />
      <FormError error={isError} />
      <DialogFooter>
        <Button
          type="submit"
          isDisabled={input.title.trim() === "" || isPending}
        >
          {submitLabel}
        </Button>
      </DialogFooter>
    </form>
  );
}

function NewTopicForm({
  slug,
  onProposed,
}: Readonly<Pick<Props, "slug" | "onProposed">>) {
  const [kind, setKind] = useState<TopicKind>("REQUEST");
  const fields = useTopicFields({});
  const propose = useProposeTopic(slug);

  return (
    <TopicForm
      fields={fields}
      kindField={
        <Select label="Kind" value={kind} options={KINDS} onChange={setKind} />
      }
      isPending={propose.isPending}
      isError={propose.isError}
      submitLabel="Propose"
      onSubmit={() =>
        propose.mutate(
          { kind, ...fields.input },
          {
            onSuccess: () => {
              fields.reset();
              onProposed?.();
            },
          },
        )
      }
    />
  );
}

function EditTopicForm({
  slug,
  onProposed,
  topicId,
  initialTitle,
  initialDescription,
  initialTags,
}: Readonly<Props & { topicId: number }>) {
  const fields = useTopicFields({
    title: initialTitle,
    description: initialDescription,
    tags: initialTags,
  });
  const edit = useEditTopic(slug);

  return (
    <TopicForm
      fields={fields}
      isPending={edit.isPending}
      isError={edit.isError}
      submitLabel="Save"
      onSubmit={() =>
        edit.mutate(
          { topicId, input: fields.input },
          { onSuccess: () => onProposed?.() },
        )
      }
    />
  );
}

/** Proposes a topic, or edits one when `topicId` is given. Only a new topic takes a kind. */
export function ProposeTopicForm(props: Readonly<Props>) {
  const { topicId } = props;
  if (topicId === undefined) {
    return <NewTopicForm {...props} />;
  }
  return <EditTopicForm {...props} topicId={topicId} />;
}
