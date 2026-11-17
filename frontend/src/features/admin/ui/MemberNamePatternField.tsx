import { ArrowDown, ArrowUp, Trash2 } from "lucide-react";
import { Input, Label, Text, TextField } from "react-aria-components";

import { IconButton } from "@/components/ui/IconButton";
import type { PatternRow } from "@/features/admin/domain/member-name-rows";

interface PatternFieldProps {
  row: PatternRow;
  position: number;
  count: number;
  canWrite: boolean;
  errorMessage: string | null;
  inputRef: (element: HTMLInputElement | null) => void;
  onFocus: () => void;
  onChange: (text: string) => void;
  onMove: (offset: -1 | 1) => void;
  onRemove: () => void;
}

export function PatternField({
  row,
  position,
  count,
  canWrite,
  errorMessage,
  inputRef,
  onFocus,
  onChange,
  onMove,
  onRemove,
}: Readonly<PatternFieldProps>) {
  const label = `Pattern ${position + 1}`;
  return (
    <li className="flex flex-col gap-1">
      <div className="flex items-start gap-2">
        <TextField
          value={row.text}
          onChange={onChange}
          isReadOnly={!canWrite}
          isInvalid={errorMessage !== null}
          className="flex min-w-0 flex-1 flex-col gap-1"
        >
          <Label className="sr-only">{label}</Label>
          <Input
            ref={inputRef}
            onFocus={onFocus}
            placeholder="{family}, {given}-SUFFIX"
            className="border-border bg-surface text-foreground placeholder:text-muted-foreground focus-visible:ring-focus-ring data-[invalid]:border-danger w-full rounded-md border px-3 py-2 font-mono text-sm outline-none focus-visible:ring-2"
          />
          {errorMessage !== null && (
            <Text
              slot="errorMessage"
              elementType="p"
              role="alert"
              className="text-danger text-sm"
            >
              {errorMessage}
            </Text>
          )}
        </TextField>
        {canWrite && (
          <div className="flex flex-none items-center">
            <IconButton
              label={`Move ${label} up`}
              isDisabled={position === 0}
              onPress={() => onMove(-1)}
            >
              <ArrowUp />
            </IconButton>
            <IconButton
              label={`Move ${label} down`}
              isDisabled={position === count - 1}
              onPress={() => onMove(1)}
            >
              <ArrowDown />
            </IconButton>
            <IconButton
              label={`Remove ${label}`}
              tone="danger"
              onPress={onRemove}
            >
              <Trash2 />
            </IconButton>
          </div>
        )}
      </div>
    </li>
  );
}
