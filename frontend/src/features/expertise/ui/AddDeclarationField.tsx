import { type ReactNode, useState } from "react";

import { AddTagField } from "@/components/patterns/AddTagField";
import { useDeclareArea } from "@/features/expertise/api/use-declarations";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import { declarationErrorMessage } from "@/features/expertise/declaration-errors";
import { plural } from "@/lib/plural";

interface Props {
  slug: string;
  /** Leads the row, e.g. the community to declare in. */
  picker?: ReactNode;
}

/** Suggests the community's existing tags first, so a declaration lands on a tag its curators already tend. */
export function AddDeclarationField({ slug, picker }: Readonly<Props>) {
  // A refusal belongs to the community it was sent to.
  const [sentSlug, setSentSlug] = useState<string | null>(null);
  const declare = useDeclareArea(slug);
  const areas = useExpertiseAreas(slug);

  const options = (areas.data ?? []).map((area) => ({
    id: area.canonicalTag,
    label: area.canonicalTag,
    hint: plural(area.peopleCount, "person"),
  }));

  return (
    <AddTagField
      label="Add an area"
      placeholder="Add an area, e.g. terraform"
      options={options}
      isPending={declare.isPending}
      errorMessage={
        declare.isError && sentSlug === slug
          ? declarationErrorMessage(declare.error)
          : undefined
      }
      onTextChange={() => {
        if (declare.isError) declare.reset();
      }}
      onAdd={(tag, done) => {
        setSentSlug(slug);
        declare.mutate(tag, { onSuccess: done });
      }}
      picker={picker}
    />
  );
}
