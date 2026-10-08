import { TagInput } from "@/components/ui/TagInput";

interface Props {
  value: string[];
  onChange: (tags: string[]) => void;
}

/**
 * What a talk was about, for a slot that carries no backlog topic: the only thing that lets a
 * backfilled or ad-hoc talk feed the expertise graph. Never offered beside a topic, since a slot
 * has a topic or its own tags, never both.
 *
 * Called "Topics" and not "Tags": a reader of this app already knows what a topic is, and "tag"
 * is the storage word, not the domain word.
 */
export function SlotTagsField({ value, onChange }: Readonly<Props>) {
  return (
    <TagInput
      label="Topics"
      value={value}
      onChange={onChange}
      placeholder="Add a topic and press Enter"
    />
  );
}
