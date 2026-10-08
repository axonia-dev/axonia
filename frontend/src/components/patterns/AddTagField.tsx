import { type KeyboardEvent, type ReactNode, useState } from "react";

import { Button } from "@/components/ui/Button";
import { ComboBox, type ComboBoxOption } from "@/components/ui/ComboBox";

interface Props {
  label: string;
  placeholder: string;
  options: ReadonlyArray<ComboBoxOption>;
  isPending: boolean;
  errorMessage?: string | undefined;
  /** Called with the typed text or a picked option's id; `done` clears the text once the tag is stored. */
  onAdd: (tag: string, done: () => void) => void;
  onTextChange?: () => void;
  /** Leads the row, e.g. the community to add in. */
  picker?: ReactNode;
}

function confirmsComposition(event: KeyboardEvent): boolean {
  return (
    event.nativeEvent.isComposing ||
    // eslint-disable-next-line sonarjs/deprecation -- Safari confirms a composition with isComposing false, flagged only by keyCode 229
    event.nativeEvent.keyCode === 229
  );
}

/** Enter on the field itself, not on a suggestion the arrow keys moved to, nor one confirming an IME composition. */
function isEnterOnTypedText(event: KeyboardEvent): boolean {
  return (
    event.key === "Enter" &&
    !confirmsComposition(event) &&
    event.target instanceof HTMLInputElement &&
    !event.target.hasAttribute("aria-activedescendant")
  );
}

/** A free-text tag field with suggestions: owns only the text, and leaves storing the tag to its owner. */
export function AddTagField({
  label,
  placeholder,
  options,
  isPending,
  errorMessage,
  onAdd,
  onTextChange,
  picker,
}: Readonly<Props>) {
  const [text, setText] = useState("");

  function add(tag: string) {
    if (isPending || tag.trim() === "") return;
    onAdd(tag, () => setText(""));
  }

  function changeText(next: string) {
    setText(next);
    onTextChange?.();
  }

  // The combobox swallows Enter while suggestions are open and only closes the list, so the
  // typed text is added here, before it gets there.
  function addOnEnter(event: KeyboardEvent) {
    if (isEnterOnTypedText(event)) add(text);
  }

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
      {picker !== undefined && (
        <div className="sm:w-48 sm:shrink-0">{picker}</div>
      )}
      <div className="flex min-w-0 flex-1 items-start gap-2">
        <div className="min-w-0 flex-1" onKeyDownCapture={addOnEnter}>
          <ComboBox
            label={label}
            hideLabel
            value={text}
            options={options}
            onInputChange={changeText}
            onSelect={(option) => add(option.id)}
            placeholder={placeholder}
            {...(errorMessage === undefined ? {} : { errorMessage })}
          />
        </div>
        <Button
          variant="secondary"
          isPending={isPending}
          onPress={() => add(text)}
        >
          Add
        </Button>
      </div>
    </div>
  );
}
