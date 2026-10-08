import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { SortableList } from "@/components/ui/SortableList";
import { TextInput } from "@/components/ui/TextInput";
import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";
import type { SectionType } from "@/features/events/domain/section-type";
import {
  emptyTemplate,
  totalMinutes,
} from "@/features/events/domain/structure";
import { BudgetBar } from "@/features/events/ui/BudgetBar";

interface Props {
  value: SectionTemplateInput[];
  onChange: (next: SectionTemplateInput[]) => void;
  sectionTypes: SectionType[];
  /** Session time budget the sections should fit within; null when unset. */
  budgetMinutes?: number | null;
}

export function StructureEditor({
  value,
  onChange,
  sectionTypes,
  budgetMinutes = null,
}: Readonly<Props>) {
  const options = sectionTypes.map((f) => ({ id: f.id, label: f.label }));
  // Templates have no stable id, so reorder by array index. The string ids
  // index back into `value` when dnd-kit hands us the new order.
  const ids = value.map((_, index) => String(index));

  function patch(index: number, partial: Partial<SectionTemplateInput>) {
    onChange(value.map((t, i) => (i === index ? { ...t, ...partial } : t)));
  }
  function add() {
    onChange([...value, emptyTemplate(sectionTypes[0]?.id ?? 0)]);
  }
  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }
  function handleReorder(nextIds: string[]) {
    onChange(
      nextIds
        .map((id) => value[Number(id)])
        .filter((t): t is SectionTemplateInput => t !== undefined),
    );
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
      <p className="text-foreground text-sm font-medium">Default structure</p>
      <SortableList ids={ids} onReorder={handleReorder}>
        {(id, handle) => {
          const index = Number(id);
          const section = value[index];
          if (!section) {
            return null;
          }
          const sectionType = sectionTypes.find(
            (f) => f.id === section.sectionTypeId,
          );
          const label = sectionType?.label ?? "Section";
          return (
            <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-md border p-3">
              <div className="flex items-center gap-2">
                {/* Only the grip carries the drag listeners, so the section-type/title/
                    minutes inputs below stay fully interactive. The handle is
                    keyboard-accessible (Space → Arrow → Space) via dnd-kit's
                    KeyboardSensor. */}
                <button
                  type="button"
                  aria-label={`Reorder section ${index + 1}`}
                  className="text-border-strong cursor-grab touch-none select-none"
                  {...handle}
                >
                  ⠿
                </button>
                <span className="text-foreground flex-1 text-sm font-semibold">
                  Section {index + 1}{" "}
                  <span className="text-muted-foreground font-normal">
                    · {label}
                  </span>
                </span>
                <button
                  type="button"
                  aria-label={`Remove section ${index + 1}`}
                  className="text-muted-foreground px-1"
                  onClick={() => remove(index)}
                >
                  ×
                </button>
              </div>
              <Select
                label="Section type"
                hideLabel
                value={section.sectionTypeId}
                options={options}
                onChange={(fid) => patch(index, { sectionTypeId: fid })}
              />
              <div className="grid grid-cols-[minmax(0,1fr)_84px] gap-2">
                <TextInput
                  label="Title"
                  value={section.title ?? ""}
                  onChange={(v) =>
                    patch(index, { title: v.trim() === "" ? null : v })
                  }
                  placeholder="optional"
                />
                <TextInput
                  label="Minutes"
                  type="number"
                  value={
                    section.durationMinutes === null
                      ? ""
                      : String(section.durationMinutes)
                  }
                  onChange={(v) =>
                    patch(index, {
                      durationMinutes: v === "" ? null : Number(v),
                    })
                  }
                />
              </div>
              {sectionType && !sectionType.expectsPresenter && (
                <p className="text-secondary text-xs">
                  ◆ No presenter, open discussion
                </p>
              )}
            </div>
          );
        }}
      </SortableList>
      <BudgetBar
        usedMinutes={totalMinutes(value)}
        budgetMinutes={budgetMinutes}
      />
      <div>
        <Button type="button" variant="ghost" size="sm" onPress={add}>
          + Add section
        </Button>
      </div>
    </div>
  );
}
