import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { SortableList } from "@/components/ui/SortableList";
import { TextInput } from "@/components/ui/TextInput";
import {
  useAddSection,
  useRemoveSection,
  useReorderSections,
  useUpdateSection,
} from "@/features/events/api/use-session-structure";
import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";
import type { Section } from "@/features/events/domain/section";
import type { SectionType } from "@/features/events/domain/section-type";
import type { Session } from "@/features/events/domain/session";
import { BudgetBar } from "@/features/events/ui/BudgetBar";

interface Props {
  slug: string;
  session: Session;
  sectionTypes: SectionType[];
  /** The length the sections are measured against. The dialog's live draft rather than the stored
   *  `session.durationMinutes`, so the meter answers the number being typed. */
  budgetMinutes: number | null;
  /** Given when the length is the reader's to change. The editor never writes it: the length is a
   *  session fact, committed once by the dialog's footer alongside whatever else it holds. */
  onBudgetChange?: (minutes: number | null) => void;
}

export function SessionStructureEditor({
  slug,
  session,
  sectionTypes,
  budgetMinutes,
  onBudgetChange,
}: Readonly<Props>) {
  const add = useAddSection(slug, session.id);
  const update = useUpdateSection(slug, session.id);
  const remove = useRemoveSection(slug, session.id);
  const reorder = useReorderSections(slug, session.id);

  const options = sectionTypes.map((f) => ({ id: f.id, label: f.label }));
  const sections = [...session.sections].sort(
    (a, b) => a.position - b.position,
  );
  const ids = sections.map((s) => String(s.id));
  const usedMinutes = sections.reduce(
    (sum, s) => sum + (s.durationMinutes ?? 0),
    0,
  );

  function handleReorder(nextIds: string[]) {
    reorder.mutate(nextIds.map((id) => Number(id)));
  }

  if (sectionTypes.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        No section types yet. Create a section type before shaping a structure.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <SortableList ids={ids} onReorder={handleReorder}>
        {(id, handle) => {
          const index = sections.findIndex((s) => String(s.id) === id);
          const section = sections[index];
          if (!section) {
            return null;
          }
          return (
            <SectionRow
              key={section.id}
              section={section}
              index={index}
              options={options}
              handle={handle}
              onUpdate={(input) =>
                update.mutate({ sectionId: section.id, input })
              }
              onRemove={() => remove.mutate(section.id)}
            />
          );
        }}
      </SortableList>
      <BudgetBar
        usedMinutes={usedMinutes}
        budgetMinutes={budgetMinutes}
        {...(onBudgetChange === undefined ? {} : { onBudgetChange })}
      />
      <div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onPress={() =>
            add.mutate({
              sectionTypeId: sectionTypes[0]?.id ?? 0,
              title: null,
              durationMinutes: null,
            })
          }
        >
          + Add section
        </Button>
      </div>
    </div>
  );
}

interface RowProps {
  section: Section;
  index: number;
  options: { id: number; label: string }[];
  handle: Parameters<Parameters<typeof SortableList>[0]["children"]>[1];
  onUpdate: (input: SectionTemplateInput) => void;
  onRemove: () => void;
}

function SectionRow({
  section,
  index,
  options,
  handle,
  onUpdate,
  onRemove,
}: Readonly<RowProps>) {
  const [title, setTitle] = useState(section.title ?? "");
  const [minutes, setMinutes] = useState(
    section.durationMinutes === null ? "" : String(section.durationMinutes),
  );
  const hasSlots = section.slots.length > 0;

  function template(
    overrides: Partial<SectionTemplateInput>,
  ): SectionTemplateInput {
    return {
      sectionTypeId: section.sectionTypeId ?? options[0]?.id ?? 0,
      title: title.trim() === "" ? null : title,
      durationMinutes: minutes === "" ? null : Number(minutes),
      ...overrides,
    };
  }

  function commitTitle() {
    const next = title.trim() === "" ? null : title;
    if (next !== section.title) {
      onUpdate(template({ title: next }));
    }
  }

  function commitMinutes() {
    const next = minutes === "" ? null : Number(minutes);
    if (next !== section.durationMinutes) {
      onUpdate(template({ durationMinutes: next }));
    }
  }

  return (
    <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-md border p-3">
      <div className="flex items-center gap-2">
        <button
          type="button"
          aria-label={`Reorder section ${index + 1}`}
          className="text-border-strong cursor-grab touch-none select-none"
          {...handle}
        >
          ⠿
        </button>
        <span className="text-foreground flex-1 text-sm font-semibold">
          Section {index + 1}
        </span>
        <button
          type="button"
          aria-label={`Remove section ${index + 1}`}
          className="text-muted-foreground px-1 disabled:opacity-40"
          disabled={hasSlots}
          title={
            hasSlots
              ? "Clear this section's topics before removing it"
              : undefined
          }
          onClick={onRemove}
        >
          ×
        </button>
      </div>
      <Select
        label="Section type"
        hideLabel
        value={section.sectionTypeId}
        options={options}
        onChange={(fid) => onUpdate(template({ sectionTypeId: fid }))}
      />
      <div className="grid grid-cols-[minmax(0,1fr)_84px] gap-2">
        <TextInput
          label="Title"
          value={title}
          onChange={setTitle}
          onBlur={commitTitle}
          placeholder="optional"
        />
        <TextInput
          label="Minutes"
          type="number"
          value={minutes}
          onChange={setMinutes}
          onBlur={commitMinutes}
        />
      </div>
    </div>
  );
}
