import type { ReactNode } from "react";
import {
  Label,
  Text,
  TextArea as AriaTextArea,
  TextField,
} from "react-aria-components";

import { cn } from "@/lib/cn";

interface TextAreaProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  isRequired?: boolean;
  rows?: number;
  placeholder?: string;
  className?: string;
  maxLength?: number;
  description?: string;
  /** A mark of what the field holds, drawn inside it. Decoration only: it is hidden from
   *  assistive tech, so it may never carry a fact the label does not already say. */
  icon?: ReactNode;
}

const areaClass =
  "w-full resize-y rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground outline-none transition-[border-color,box-shadow] placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-focus-ring";

export function TextArea({
  label,
  value,
  onChange,
  isRequired,
  rows = 4,
  placeholder,
  className,
  maxLength,
  description,
  icon,
}: Readonly<TextAreaProps>) {
  return (
    <TextField
      value={value}
      onChange={onChange}
      {...(isRequired !== undefined ? { isRequired } : {})}
      className={cn("flex flex-col gap-1", className)}
    >
      <Label className="text-foreground text-sm font-medium">{label}</Label>
      {/* The same relative wrapper SearchField uses, pinned to the first line rather than
          centred: a box several rows tall would otherwise float its mark halfway down. The
          wrapper is always present - one inert div - but carries no styling of its own, so a box
          with no icon keeps the exact border, padding and focus ring it always had. */}
      <div className="relative">
        {icon !== undefined && (
          <span
            aria-hidden
            className="text-muted-foreground pointer-events-none absolute top-2.5 left-3 flex items-center"
          >
            {icon}
          </span>
        )}
        <AriaTextArea
          rows={rows}
          {...(placeholder !== undefined ? { placeholder } : {})}
          {...(maxLength !== undefined ? { maxLength } : {})}
          className={cn(areaClass, icon !== undefined && "pl-9")}
        />
      </div>
      {/* Slotted rather than a bare <p>, for the reason TextInput's own description records:
          react-aria wires `aria-describedby` only for its own Text. */}
      {description !== undefined && (
        <Text
          slot="description"
          elementType="p"
          className="text-muted-foreground text-xs"
        >
          {description}
        </Text>
      )}
    </TextField>
  );
}
