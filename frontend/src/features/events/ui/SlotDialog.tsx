import { type ReactNode, useState } from "react";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import { Dialog } from "@/components/ui/Dialog";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { Select } from "@/components/ui/Select";
import { TextInput } from "@/components/ui/TextInput";
import {
  usePresentFromBacklog,
  useQueueTopic,
} from "@/features/events/api/use-backlog-pull";
import { useAddSlot } from "@/features/events/api/use-slot-sign-up";
import { useTopics } from "@/features/events/api/use-topics";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { EditSlotForm } from "@/features/events/ui/EditSlotForm";
import { RecordSlotForm } from "@/features/events/ui/RecordSlotForm";
import {
  anyPending,
  canAdd,
  DEFAULT_MINUTES,
  firstError,
  MODE_OPTIONS,
  pickSelected,
  PROPOSED_PARAMS,
  proposedTopics,
  resolveSource,
  type SlotMode,
  type Source,
  SOURCE_OPTIONS,
  topicDescription,
} from "@/features/events/ui/slot-dialog-helpers";
import { SlotFormTail } from "@/features/events/ui/SlotFormTail";
import { SlotTagsField } from "@/features/events/ui/SlotTagsField";
import { failedToLoad, stillLoading } from "@/lib/failed-to-load";
import { useAnsweredAfterFailing } from "@/lib/use-answered-after-failing";

type SlotDialogProps =
  | {
      mode: "add";
      slug: string;
      session: Session;
      section: Section;
      canQueueTopics?: boolean;
      isOpen: boolean;
      onOpenChange: (open: boolean) => void;
    }
  | {
      mode: "record";
      slug: string;
      session: Session;
      section: Section;
      isOpen: boolean;
      onOpenChange: (open: boolean) => void;
    }
  | {
      mode: "edit";
      slug: string;
      session: Session;
      section: Section;
      slot: Slot;
      isOpen: boolean;
      onOpenChange: (open: boolean) => void;
    };

const DIALOG_TITLES: Record<SlotDialogProps["mode"], string> = {
  add: "Add a slot",
  record: "Record a slot",
  edit: "Edit slot",
};

function renderSlotForm(
  props: Readonly<SlotDialogProps>,
  close: () => void,
): ReactNode {
  if (props.mode === "add") {
    return (
      <AddSlotForm
        slug={props.slug}
        session={props.session}
        section={props.section}
        canQueueTopics={props.canQueueTopics ?? false}
        onDone={close}
      />
    );
  }
  if (props.mode === "record") {
    return (
      <RecordSlotForm
        slug={props.slug}
        session={props.session}
        section={props.section}
        onDone={close}
      />
    );
  }
  return (
    <EditSlotForm
      slug={props.slug}
      session={props.session}
      section={props.section}
      slot={props.slot}
      onDone={close}
    />
  );
}

export function SlotDialog(props: Readonly<SlotDialogProps>) {
  return (
    <Dialog
      isOpen={props.isOpen}
      onOpenChange={props.onOpenChange}
      title={DIALOG_TITLES[props.mode]}
    >
      {({ close }) => renderSlotForm(props, close)}
    </Dialog>
  );
}

interface AddFormProps {
  slug: string;
  session: Session;
  section: Section;
  canQueueTopics: boolean;
  onDone: () => void;
}

function AddSlotForm({
  slug,
  session,
  section,
  canQueueTopics,
  onDone,
}: Readonly<AddFormProps>) {
  const [sourceChoice, setSourceChoice] = useState<Source | null>(null);
  const [subject, setSubject] = useState("");
  const [topicId, setTopicId] = useState<number | null>(null);
  const [mode, setMode] = useState<SlotMode>("present");
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES);
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState<string[]>([]);

  const topics = useTopics(slug, PROPOSED_PARAMS);
  const topicsFailed = failedToLoad(topics);
  const topicsRecovered = useAnsweredAfterFailing(topicsFailed);
  const addSlot = useAddSlot(slug, session.id, section.id);
  const present = usePresentFromBacklog(slug, session.id, section.id);
  const queue = useQueueTopic(slug, session.id, section.id);

  if (stillLoading(topics)) {
    return (
      <p className="text-muted-foreground text-sm">Loading the backlog…</p>
    );
  }

  const ranked = proposedTopics(topics.data);
  const options = ranked.map((t) => ({
    id: t.id,
    label: `▲ ${t.voteCount} · ${t.title}`,
  }));
  const selected = pickSelected(ranked, topicId);
  // A backlog that failed to load is not an empty one: the form stays on it, rather than turning
  // to a new topic that may duplicate one already proposed.
  const source = resolveSource(
    sourceChoice ?? (topicsFailed || topicsRecovered ? "backlog" : null),
    ranked.length,
  );

  const pickTopic = (id: number | null) => {
    setTopicId(id);
    setDescription(topicDescription(ranked, id));
  };
  const busy = anyPending(addSlot, present, queue);
  const error = firstError(addSlot, present, queue);

  const submit = () => {
    if (source === "newTopic") {
      addSlot.mutate(
        {
          title: subject.trim(),
          durationMinutes: minutes,
          notes: description.trim(),
          tags,
        },
        { onSuccess: onDone },
      );
      return;
    }
    if (!selected) {
      return;
    }
    // The backlog path sends no tags: the topic is the subject, and a slot carrying both is
    // refused (SLOT_TOPIC_AND_TAGS).
    const mutation = mode === "open" ? queue : present;
    mutation.mutate(
      {
        title: selected.title,
        topicId: selected.id,
        durationMinutes: minutes,
        notes: description.trim(),
      },
      { onSuccess: onDone },
    );
  };

  return (
    <div className="flex flex-col gap-3">
      <SegmentedControl
        label="Source"
        hideLabel
        value={source}
        options={SOURCE_OPTIONS}
        onChange={(value) => setSourceChoice(value as Source)}
      />

      {source === "newTopic" ? (
        <>
          <TextInput
            label="Subject"
            value={subject}
            onChange={setSubject}
            isRequired
          />
          <TextInput
            label="Description"
            value={description}
            onChange={setDescription}
          />
          <SlotTagsField value={tags} onChange={setTags} />
        </>
      ) : (
        <>
          {topicsFailed ? (
            <LoadFailed
              inline
              message="Could not load the backlog."
              query={topics}
            />
          ) : (
            <Select
              label="Topic"
              value={selected ? selected.id : null}
              options={options}
              onChange={pickTopic}
              placeholder={
                options.length === 0
                  ? "No topics in the backlog"
                  : "Choose a topic…"
              }
              takesFocus={topicsRecovered}
            />
          )}
          {canQueueTopics && (
            <Select
              label="How"
              value={mode}
              options={MODE_OPTIONS}
              onChange={setMode}
            />
          )}
          <TextInput
            label="Description"
            value={description}
            onChange={setDescription}
          />
        </>
      )}

      <SlotFormTail
        minutes={minutes}
        onMinutesChange={setMinutes}
        section={section}
        onCancel={onDone}
        onSubmit={submit}
        submitLabel="Add"
        submitDisabled={!canAdd(source, subject, selected, busy)}
        error={error}
      />
    </div>
  );
}
