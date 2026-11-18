import { type SyntheticEvent, useState } from "react";

import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { FormError } from "@/components/patterns/FormError";
import { AreaTag } from "@/components/ui/AreaTag";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import { usePropose } from "@/features/mentoring/api/use-mentoring";
import type { MentoringRole } from "@/features/mentoring/domain/mentoring";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";
import { personName } from "@/features/mentoring/ui/PartnerFace";
import {
  proposalWording,
  SHOWN_ONCE_ACCEPTED,
} from "@/features/mentoring/ui/proposal-wording";

const MAX_TOPICS = 5;
const MAX_TOPIC_LENGTH = 80;
const MAX_MESSAGE_LENGTH = 500;

interface Props {
  slug: string;
  person: { userId: string; displayName: string; areas: string[] };
  /** The role of the person proposing. */
  asRole: MentoringRole;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/** The topics a proposal sends: the chosen ones, plus a typed topic not yet added, so nothing typed is dropped. */
function withPending(topics: string[], pending: string): string[] {
  return pending === "" || topics.includes(pending)
    ? topics
    : [...topics, pending];
}

function TopicPicker({
  areas,
  topics,
  own,
  onOwnChange,
  onChange,
}: Readonly<{
  areas: string[];
  topics: string[];
  own: string;
  onOwnChange: (own: string) => void;
  onChange: (topics: string[]) => void;
}>) {
  const full = topics.length >= MAX_TOPICS;
  const ownTopic = own.trim();

  function toggle(topic: string) {
    onChange(
      topics.includes(topic)
        ? topics.filter((t) => t !== topic)
        : [...topics, topic],
    );
  }

  function addOwn(event: SyntheticEvent) {
    event.preventDefault();
    if (ownTopic === "" || full) return;
    onChange(withPending(topics, ownTopic));
    onOwnChange("");
  }

  const extras = topics.filter((topic) => !areas.includes(topic));

  return (
    <fieldset className="flex min-w-0 flex-col gap-2">
      <legend className="text-foreground text-sm font-medium">
        Topics ({topics.length} of {MAX_TOPICS})
      </legend>
      <div className="flex flex-wrap gap-2">
        {[...areas, ...extras].map((topic) => {
          const active = topics.includes(topic);
          return (
            <FilterToggleChip
              key={topic}
              active={active}
              isDisabled={!active && full}
              onToggle={() => toggle(topic)}
            >
              {areas.includes(topic) ? <AreaTag area={topic} /> : topic}
            </FilterToggleChip>
          );
        })}
      </div>
      <form onSubmit={addOwn}>
        <TextInput
          label={topics.length === 0 ? "Topic" : "Another topic"}
          value={own}
          onChange={onOwnChange}
          maxLength={MAX_TOPIC_LENGTH}
          placeholder="e.g. Giving feedback"
          action={
            <Button
              type="submit"
              variant="secondary"
              isDisabled={ownTopic === "" || full}
            >
              Add topic
            </Button>
          }
        />
      </form>
      {full && (
        <p className="text-muted-foreground text-xs">
          At most {MAX_TOPICS} topics.
        </p>
      )}
    </fieldset>
  );
}

function ProposeForm({
  slug,
  person,
  asRole,
  close,
}: Readonly<Omit<Props, "isOpen" | "onOpenChange"> & { close: () => void }>) {
  const propose = usePropose(slug);
  const [topics, setTopics] = useState<string[]>([]);
  const [own, setOwn] = useState("");
  const [message, setMessage] = useState("");
  const sent = withPending(topics, own.trim());
  const canSend =
    sent.length > 0 && sent.length <= MAX_TOPICS && message.trim() !== "";

  function send() {
    propose.mutate(
      {
        toUserId: person.userId,
        asRole,
        topics: sent,
        message: message.trim(),
      },
      { onSuccess: close },
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <TopicPicker
        areas={person.areas}
        topics={topics}
        own={own}
        onOwnChange={setOwn}
        onChange={setTopics}
      />
      <TextArea
        label="Message"
        value={message}
        onChange={setMessage}
        maxLength={MAX_MESSAGE_LENGTH}
        rows={4}
      />
      <p className="text-muted-foreground text-xs">{SHOWN_ONCE_ACCEPTED}</p>
      <FormError
        error={propose.error}
        message={mentoringErrorMessage(propose.error)}
      />
      <DialogFooter>
        <Button variant="ghost" onPress={close} isDisabled={propose.isPending}>
          Cancel
        </Button>
        <Button isDisabled={!canSend || propose.isPending} onPress={send}>
          {proposalWording(asRole).label}
        </Button>
      </DialogFooter>
    </div>
  );
}

/** The proposer picks up to five topics from the person's areas or types their own, then says why. */
export function ProposeDialog({
  isOpen,
  onOpenChange,
  ...form
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={proposalWording(form.asRole).named(
        personName(form.person.displayName),
      )}
    >
      <ProposeForm {...form} close={() => onOpenChange(false)} />
    </Dialog>
  );
}
