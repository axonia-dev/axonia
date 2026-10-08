import type { CSSProperties } from "react";

import { Button } from "@/components/ui/Button";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { typeAccent, TypeIcon } from "@/features/knowledge/ui/type-visual";
import { cn } from "@/lib/cn";

export const ALL_TYPES = 0;

interface Props {
  selected: number;
  onSelect: (typeId: number) => void;
}

const chipBase =
  "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors";

function AllChip({
  active,
  onSelect,
}: Readonly<{ active: boolean; onSelect: () => void }>) {
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-pressed={active}
      onPress={onSelect}
      className={cn(
        chipBase,
        active
          ? "border-primary bg-primary text-primary-foreground hover:bg-primary-strong"
          : "border-border bg-surface text-muted-foreground hover:border-border-strong hover:bg-surface hover:text-foreground shadow-sm",
      )}
    >
      All
    </Button>
  );
}

/**
 * A chip row over the artifact-type vocabulary, replacing the type dropdown.
 * Each type carries its icon + accent so the row reads as the library's spine.
 * Selecting the active chip (or "All") clears the type filter.
 */
export function TypeChips({ selected, onSelect }: Readonly<Props>) {
  const types = useArtifactTypes();

  function toggle(typeId: number) {
    onSelect(selected === typeId ? ALL_TYPES : typeId);
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <AllChip
        active={selected === ALL_TYPES}
        onSelect={() => onSelect(ALL_TYPES)}
      />
      {(types.data ?? []).map((type) => {
        const active = selected === type.id;
        return (
          <Button
            key={type.id}
            variant="ghost"
            size="sm"
            aria-pressed={active}
            onPress={() => toggle(type.id)}
            style={
              {
                "--k": type.accentColor ?? typeAccent(type.id),
              } as CSSProperties
            }
            className={cn(
              chipBase,
              active
                ? "border-[var(--k)] bg-[color-mix(in_srgb,var(--k)_18%,var(--surface))] text-[color-mix(in_srgb,var(--k)_85%,var(--foreground))] hover:bg-[color-mix(in_srgb,var(--k)_28%,var(--surface))]"
                : "border-border bg-surface text-muted-foreground hover:border-border-strong hover:bg-surface hover:text-foreground shadow-sm",
            )}
          >
            <TypeIcon
              typeId={type.id}
              label={type.label}
              icon={type.icon}
              accentColor={type.accentColor}
              size={14}
              className="bg-transparent p-0"
            />
            {type.label}
          </Button>
        );
      })}
    </div>
  );
}
