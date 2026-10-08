import { Lock, Plus } from "lucide-react";
import { useId, useState } from "react";
import { Input, Label, TextField } from "react-aria-components";

import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

interface TagInputProps {
  label: string;
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  /** Replaces the generic hint below the field, when the field needs to explain itself. */
  description?: string;
  /** Rejects a candidate entry with a message shown below the field; null accepts it. */
  validate?: (candidate: string) => string | null;
  /** Reports rejection-state transitions so a parent can hold back submission. */
  onErrorChange?: (error: string | null) => void;
  /** Entries the value always carries, drawn first behind a lock and never removable. */
  lockedTags?: readonly string[];
  /** Known entries offered as add chips for as long as the value does not name them. */
  suggestedTags?: readonly string[];
  /** Characters that commit the typed text and split a pasted list. Defaults to a comma. */
  separators?: readonly string[];
}

/**
 * The input shares the bordered box with the chips rather than nesting a second bordered field
 * inside it, and fills its lane so a placeholder is never clipped. The box itself carries the
 * border and the focus ring (focus-within on the container).
 */
const inputClass =
  "block w-full bg-transparent px-1.5 py-0.5 text-sm text-foreground outline-none placeholder:text-muted-foreground";

const chipClass =
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium";

const DEFAULT_SEPARATORS: readonly string[] = [","];

function sameTag(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase();
}

function addTag(tags: string[], tag: string): string[] {
  if (tags.some((t) => sameTag(t, tag))) return tags;
  return [...tags, tag];
}

function splitCandidates(raw: string, separators: readonly string[]): string[] {
  return separators
    .reduce<string[]>(
      (parts, separator) => parts.flatMap((part) => part.split(separator)),
      [raw],
    )
    .map((candidate) => candidate.trim())
    .filter(Boolean);
}

export function TagInput({
  label,
  value,
  onChange,
  placeholder,
  description,
  validate,
  onErrorChange,
  lockedTags = [],
  suggestedTags = [],
  separators = DEFAULT_SEPARATORS,
}: Readonly<TagInputProps>) {
  const [inputValue, setInputValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const errorId = useId();

  const known = [...lockedTags, ...value];
  const missing = suggestedTags.filter(
    (tag) => !known.some((t) => sameTag(t, tag)),
  );

  function updateError(next: string | null) {
    setError(next);
    onErrorChange?.(next);
  }

  function firstRejection(candidates: string[]): string | null {
    if (validate === undefined) return null;
    for (const candidate of candidates) {
      const message = validate(candidate);
      if (message !== null) return message;
    }
    return null;
  }

  function commitCurrentInput(raw: string) {
    // A locked entry is already in what gets submitted, so typing its name is a no-op rather than
    // a second chip beside the locked one saying the same thing.
    const candidates = splitCandidates(raw, separators).filter(
      (candidate) => !lockedTags.some((t) => sameTag(t, candidate)),
    );
    const rejection = firstRejection(candidates);
    if (rejection !== null) {
      updateError(rejection);
      return;
    }
    const next = candidates.reduce(addTag, value);
    if (next !== value) onChange(next);
    setInputValue("");
  }

  function handleInputChange(next: string) {
    setInputValue(next);
    updateError(null);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" || separators.includes(e.key)) {
      e.preventDefault();
      commitCurrentInput(inputValue);
    } else if (e.key === "Backspace" && inputValue === "" && value.length > 0) {
      updateError(null);
      onChange(value.slice(0, -1));
    }
  }

  function handleBlur() {
    if (inputValue.trim() !== "") {
      commitCurrentInput(inputValue);
    }
  }

  function removeTag(tag: string) {
    updateError(null);
    onChange(value.filter((t) => t !== tag));
  }

  function addSuggestion(tag: string) {
    updateError(null);
    onChange(addTag(value, tag));
  }

  return (
    <div className="flex flex-col gap-1">
      <span className="text-foreground text-sm font-medium">{label}</span>
      <div className="border-border bg-surface focus-within:ring-focus-ring flex flex-wrap gap-1.5 rounded-md border p-1.5 focus-within:ring-2">
        {lockedTags.map((tag) => (
          <span
            key={tag}
            className={cn(
              chipClass,
              "bg-surface-sunken text-muted-foreground border-border",
            )}
          >
            <Lock size={11} aria-hidden />
            {tag}
          </span>
        ))}
        {value.map((tag) => (
          <span
            key={tag}
            className={cn(
              chipClass,
              "bg-surface-sunken text-foreground border-border",
            )}
          >
            {tag}
            <Button
              variant="ghost"
              size="sm"
              aria-label={`Remove tag ${tag}`}
              onPress={() => removeTag(tag)}
              className="text-muted-foreground hover:text-foreground ml-0.5 h-4 w-4 rounded-full p-0"
            >
              ×
            </Button>
          </span>
        ))}
        {missing.map((tag) => (
          <Button
            key={tag}
            variant="ghost"
            size="sm"
            aria-label={`Add ${tag}`}
            onPress={() => addSuggestion(tag)}
            className={cn(
              chipClass,
              "border-border text-muted-foreground hover:border-border-strong hover:text-foreground border-dashed bg-transparent",
            )}
          >
            <Plus size={11} aria-hidden />
            {tag}
          </Button>
        ))}
        <TextField
          value={inputValue}
          onChange={handleInputChange}
          isInvalid={error !== null}
          // The lane is wide enough for its own placeholder, so a hint is never clipped. Without
          // one there is nothing to protect, and a wide lane only pushes itself onto a second row
          // below chips that would otherwise have left it room.
          className={cn(
            "flex-1",
            placeholder === undefined ? "min-w-24" : "min-w-48",
          )}
        >
          <Label className="sr-only">{label}</Label>
          <Input
            onKeyDown={handleKeyDown}
            onBlur={handleBlur}
            aria-describedby={error !== null ? errorId : undefined}
            {...(placeholder !== undefined ? { placeholder } : {})}
            className={inputClass}
          />
        </TextField>
      </div>
      {error !== null ? (
        <p id={errorId} role="alert" className="text-danger text-xs">
          {error}
        </p>
      ) : (
        <p className="text-muted-foreground text-xs">
          {description ?? "Press Enter or comma to add a tag"}
        </p>
      )}
    </div>
  );
}
