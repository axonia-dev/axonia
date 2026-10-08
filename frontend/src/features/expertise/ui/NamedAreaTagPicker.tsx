import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { ComboBox } from "@/components/ui/ComboBox";
import type { ComboBoxOption } from "@/components/ui/ComboBoxSuggestions";
import { DialogFooter } from "@/components/ui/DialogFooter";
import type { NamedArea } from "@/features/expertise/domain/named-area";

interface Props {
  namedArea: NamedArea;
  namedAreas: NamedArea[];
  /** The community's canonical tags. */
  vocabulary: string[];
  onPlace: (tag: string) => void;
}

interface Move {
  tag: string;
  from: string;
}

function holderOf(tag: string, namedAreas: NamedArea[]): NamedArea | undefined {
  return namedAreas.find((namedArea) => namedArea.tags.includes(tag));
}

function optionsFor({
  namedArea,
  namedAreas,
  vocabulary,
}: Readonly<Omit<Props, "onPlace">>): ComboBoxOption[] {
  return vocabulary
    .filter((tag) => !namedArea.tags.includes(tag))
    .map((tag) => {
      const holder = holderOf(tag, namedAreas);
      return holder === undefined
        ? { id: tag, label: tag }
        : { id: tag, label: tag, hint: `in ${holder.name}` };
    });
}

/** A tag sits in at most one named area, so taking one from another asks first. */
export function NamedAreaTagPicker(props: Readonly<Props>) {
  const { namedArea, namedAreas, onPlace } = props;
  const [text, setText] = useState("");
  const [move, setMove] = useState<Move | null>(null);

  function pick(tag: string) {
    setText("");
    const holder = holderOf(tag, namedAreas);
    if (holder === undefined) onPlace(tag);
    else setMove({ tag, from: holder.name });
  }

  function confirmMove(tag: string) {
    setMove(null);
    onPlace(tag);
  }

  return (
    <div className="flex flex-col gap-2">
      <ComboBox
        label={`Add a tag to ${namedArea.name}`}
        hideLabel
        value={text}
        options={optionsFor(props)}
        onInputChange={setText}
        onSelect={(option) => pick(option.id)}
        placeholder="Add a tag"
      />
      {move !== null && (
        <div className="border-border bg-surface-sunken flex flex-wrap items-center justify-between gap-2 rounded-md border px-3 py-2">
          <p className="text-foreground text-sm">
            {`Moving ${move.tag} takes it out of ${move.from}.`}
          </p>
          <DialogFooter className="ml-auto">
            <Button variant="ghost" size="sm" onPress={() => setMove(null)}>
              Cancel
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onPress={() => confirmMove(move.tag)}
            >
              Move
            </Button>
          </DialogFooter>
        </div>
      )}
    </div>
  );
}
