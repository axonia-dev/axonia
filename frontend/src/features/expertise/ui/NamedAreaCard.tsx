import { Pencil, Trash2, X } from "lucide-react";
import { useId } from "react";

import { IconButton } from "@/components/ui/IconButton";
import type { NamedArea } from "@/features/expertise/domain/named-area";
import { NamedAreaTagPicker } from "@/features/expertise/ui/NamedAreaTagPicker";

export interface NamedAreaCardProps {
  namedArea: NamedArea;
  namedAreas: NamedArea[];
  vocabulary: string[];
  /** The tags the map draws, or null while it is unknown. */
  onMap: ReadonlySet<string> | null;
  onRename: (namedArea: NamedArea) => void;
  onDelete: (namedArea: NamedArea) => void;
  onPlace: (id: number, tag: string) => void;
  onTakeOut: (tag: string) => void;
}

interface ChipProps {
  tag: string;
  namedAreaName: string;
  offMap: boolean;
  onTakeOut: (tag: string) => void;
}

function StoredTagChip({
  tag,
  namedAreaName,
  offMap,
  onTakeOut,
}: Readonly<ChipProps>) {
  return (
    <li className="bg-surface-sunken text-foreground inline-flex items-center gap-1.5 rounded-full py-0.5 pr-1 pl-2.5 text-xs">
      <span>{tag}</span>
      {offMap && <span className="text-muted-foreground">not on the map</span>}
      <button
        type="button"
        aria-label={`Take ${tag} out of ${namedAreaName}`}
        onClick={() => onTakeOut(tag)}
        className="text-muted-foreground hover:text-foreground -mx-1 -my-1 cursor-pointer rounded-full p-1.5 transition-colors"
      >
        <X aria-hidden className="size-3" />
      </button>
    </li>
  );
}

/** One named area: its name, the tags it holds as stored, and the way to add more. */
export function NamedAreaCard(props: Readonly<NamedAreaCardProps>) {
  const { namedArea, onMap, onRename, onDelete, onPlace, onTakeOut } = props;
  const headingId = useId();

  return (
    <section
      aria-labelledby={headingId}
      className="border-border bg-surface flex flex-col gap-3 rounded-xl border p-3.5 shadow-sm"
    >
      <div className="flex items-center justify-between gap-2">
        <h3
          id={headingId}
          className="text-foreground min-w-0 truncate text-sm font-semibold"
        >
          {namedArea.name}
        </h3>
        <div className="flex items-center">
          <IconButton
            label={`Rename ${namedArea.name}`}
            onPress={() => onRename(namedArea)}
          >
            <Pencil aria-hidden />
          </IconButton>
          <IconButton
            tone="danger"
            label={`Delete ${namedArea.name}`}
            onPress={() => onDelete(namedArea)}
          >
            <Trash2 aria-hidden />
          </IconButton>
        </div>
      </div>
      {namedArea.tags.length === 0 ? (
        <p className="text-muted-foreground text-xs">Holds no tags.</p>
      ) : (
        <ul className="flex flex-wrap gap-1.5">
          {namedArea.tags.map((tag) => (
            <StoredTagChip
              key={tag}
              tag={tag}
              namedAreaName={namedArea.name}
              offMap={onMap !== null && !onMap.has(tag)}
              onTakeOut={onTakeOut}
            />
          ))}
        </ul>
      )}
      <NamedAreaTagPicker
        namedArea={namedArea}
        namedAreas={props.namedAreas}
        vocabulary={props.vocabulary}
        onPlace={(tag) => onPlace(namedArea.id, tag)}
      />
    </section>
  );
}
