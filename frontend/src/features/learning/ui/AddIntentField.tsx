import { useState } from "react";

import { AddTagField } from "@/components/patterns/AddTagField";
import type { ComboBoxOption } from "@/components/ui/ComboBox";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { useAddIntent } from "@/features/learning/api/use-learning";
import type { WantedArea } from "@/features/learning/domain/learning";
import { learningErrorMessage } from "@/features/learning/learning-errors";
import { plural } from "@/lib/plural";

interface Props {
  slug: string;
  /** The member's own areas to learn, which are not suggested again. */
  held: string[];
  wanted: WantedArea[];
}

function suggestions(
  areas: ExpertiseArea[],
  wanted: WantedArea[],
  held: string[],
): ComboBoxOption[] {
  const known = new Set(areas.map((area) => area.canonicalTag));
  const options = [
    ...areas.map((area) => ({
      id: area.canonicalTag,
      label: area.canonicalTag,
      hint: plural(area.peopleCount, "person"),
    })),
    ...wanted
      .filter((area) => !known.has(area.tag))
      .map((area) => ({
        id: area.tag,
        label: area.tag,
        hint: plural(area.learners, "learner"),
      })),
  ];
  return options.filter((option) => !held.includes(option.id));
}

/** Suggests the community's areas first, then the ones only wanted, so an intent lands on a tag already in use. */
export function AddIntentField({ slug, held, wanted }: Readonly<Props>) {
  // A refusal belongs to the community it was sent to.
  const [sentSlug, setSentSlug] = useState<string | null>(null);
  const add = useAddIntent(slug);
  const areas = useExpertiseAreas(slug);

  return (
    <AddTagField
      label="Add an area to learn"
      placeholder="Add an area, e.g. kubernetes"
      options={suggestions(areas.data ?? [], wanted, held)}
      isPending={add.isPending}
      errorMessage={
        add.isError && sentSlug === slug
          ? learningErrorMessage(add.error)
          : undefined
      }
      onTextChange={() => {
        if (add.isError) add.reset();
      }}
      onAdd={(tag, done) => {
        setSentSlug(slug);
        add.mutate(tag, { onSuccess: done });
      }}
    />
  );
}
