import type { ComponentProps } from "react";

import { DisclosureSection } from "@/components/ui/DisclosureSection";
import {
  SectionTypesGate,
  type SectionTypesRead,
} from "@/features/events/ui/SectionTypesGate";
import { structureSummary } from "@/features/events/ui/series-form-summaries";
import { StructureEditor } from "@/features/events/ui/StructureEditor";

type Props = Omit<ComponentProps<typeof StructureEditor>, "sectionTypes"> &
  Readonly<{ title: string; sectionTypes: SectionTypesRead }>;

/** A form's structure: shut until it holds a section, and editable once the section types are in
 *  hand. */
export function StructureSection({ title, sectionTypes, ...editor }: Props) {
  return (
    <DisclosureSection
      title={title}
      summary={structureSummary(editor.value)}
      defaultExpanded={editor.value.length > 0}
    >
      <SectionTypesGate sectionTypes={sectionTypes}>
        {(loaded) => <StructureEditor {...editor} sectionTypes={loaded} />}
      </SectionTypesGate>
    </DisclosureSection>
  );
}
