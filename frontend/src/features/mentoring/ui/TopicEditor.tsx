import { type SyntheticEvent, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Select, type SelectOption } from "@/components/ui/Select";
import { TextInput } from "@/components/ui/TextInput";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import type { Topic } from "@/features/mentoring/domain/mentoring";

const MAX_LABEL_LENGTH = 80;
const NO_AREA = "";

interface Props {
  topic: Topic;
  communitySlug: string;
  isPending: boolean;
  onSave: (label: string, area: string | null) => void;
}

function areaOptions(
  tags: string[],
  current: string | null,
): SelectOption<string>[] {
  const known =
    current === null || tags.includes(current) ? tags : [current, ...tags];
  return [
    { id: NO_AREA, label: "No area" },
    ...known.map((tag) => ({ id: tag, label: tag })),
  ];
}

/** Renames a topic and links it to one of the guild's areas, or to none. */
export function TopicEditor({
  topic,
  communitySlug,
  isPending,
  onSave,
}: Readonly<Props>) {
  const [label, setLabel] = useState(topic.label);
  const [area, setArea] = useState(topic.area ?? NO_AREA);
  const areas = useExpertiseAreas(communitySlug);
  const tags = (areas.data ?? []).map((entry) => entry.canonicalTag);
  const trimmed = label.trim();
  const unchanged = trimmed === topic.label && area === (topic.area ?? NO_AREA);

  function submit(event: SyntheticEvent) {
    event.preventDefault();
    if (trimmed === "" || isPending) return;
    onSave(trimmed, area === NO_AREA ? null : area);
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-2">
      <TextInput
        label="Name"
        value={label}
        onChange={setLabel}
        maxLength={MAX_LABEL_LENGTH}
      />
      <Select
        label="Area"
        value={area}
        options={areaOptions(tags, topic.area)}
        onChange={setArea}
        isLoading={areas.isLoading}
      />
      <Button
        type="submit"
        variant="secondary"
        className="self-start"
        isDisabled={trimmed === "" || isPending || unchanged}
      >
        Save topic
      </Button>
    </form>
  );
}
