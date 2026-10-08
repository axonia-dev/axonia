import type { ReactNode } from "react";
import { Input, Label, Text, TextField } from "react-aria-components";

import { cn } from "@/lib/cn";

interface TextInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  isRequired?: boolean;
  /**
   * `"password"` masks what is typed into the box and nothing else. It is not a way to show a
   * stored value safely: a masked box drawn over something already held is the trap where the
   * asterisks read as data and are eventually saved back as the value. Only a field the reader is
   * typing into may be masked.
   */
  type?: "text" | "email" | "url" | "date" | "time" | "number" | "password";
  placeholder?: string;
  onBlur?: () => void;
  description?: string;
  /**
   * The id of an element stating something about this field that its label does not, tied to the
   * input so assistive tech reaches it. For a sentence this component may own, use `description`;
   * this exists for one the caller draws, positions and styles itself.
   */
  describedBy?: string;
  errorMessage?: string;
  className?: string;
  maxLength?: number;
  /** A mark of what the field holds, drawn inside it. Decoration only: it is hidden from
   *  assistive tech, so it may never carry a fact the label does not already say. */
  icon?: ReactNode;
  /** A control beside the input, on its line, as `ReadOnlyField` draws one beside a value. */
  action?: ReactNode;
}

// The browser's spin buttons are hidden, as BudgetBar's and NumberField's inputs show none; arrow
// keys still step.
const inputClass =
  "w-full rounded-md border border-border bg-surface px-3 py-2 text-sm text-foreground outline-none transition-[border-color,box-shadow] placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-focus-ring data-[invalid]:border-danger [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none";

interface ControlProps {
  placeholder: string | undefined;
  onBlur: (() => void) | undefined;
  maxLength: number | undefined;
  describedBy: string | undefined;
  hasIcon: boolean;
}

/**
 * The control itself, drawn from its own function because every one of these props is optional and
 * `exactOptionalPropertyTypes` makes each an omit-or-pass branch. Counted together with the label,
 * the description and the error line they put the field one over the complexity ceiling.
 */
function Control({
  placeholder,
  onBlur,
  maxLength,
  describedBy,
  hasIcon,
}: Readonly<ControlProps>) {
  return (
    <Input
      {...(placeholder !== undefined ? { placeholder } : {})}
      {...(onBlur !== undefined ? { onBlur } : {})}
      {...(maxLength !== undefined ? { maxLength } : {})}
      {...(describedBy !== undefined
        ? { "aria-describedby": describedBy }
        : {})}
      className={cn(inputClass, hasIcon && "pl-9")}
    />
  );
}

export function TextInput({
  label,
  value,
  onChange,
  isRequired,
  type = "text",
  placeholder,
  onBlur,
  description,
  describedBy,
  errorMessage,
  className,
  maxLength,
  icon,
  action,
}: Readonly<TextInputProps>) {
  return (
    <TextField
      value={value}
      onChange={onChange}
      {...(isRequired !== undefined ? { isRequired } : {})}
      isInvalid={errorMessage !== undefined}
      type={type}
      className={cn("flex flex-col gap-1", className)}
    >
      <Label className="text-foreground text-sm font-medium">{label}</Label>
      {/* The same relative wrapper SearchField uses: the icon is laid over the control rather
          than beside it. The wrapper and the row that holds an action beside it are always
          present, but neither draws anything, so a field with no icon and no action keeps the
          exact border, padding and focus ring it always had. */}
      <div className="flex items-center gap-2">
        <div className="relative min-w-0 flex-1">
          {icon !== undefined && (
            <span
              aria-hidden
              className="text-muted-foreground pointer-events-none absolute inset-y-0 left-3 flex items-center"
            >
              {icon}
            </span>
          )}
          <Control
            placeholder={placeholder}
            onBlur={onBlur}
            maxLength={maxLength}
            describedBy={describedBy}
            hasIcon={icon !== undefined}
          />
        </div>
        {action}
      </div>
      {/* react-aria wires `aria-describedby` only for its own slotted Text: a bare <p> here left
          every description sighted-only. `elementType` keeps the rendered markup a paragraph. */}
      {description !== undefined && (
        <Text
          slot="description"
          elementType="p"
          className="text-muted-foreground text-xs"
        >
          {description}
        </Text>
      )}
      {/* The slot ties the error to the input, as the description is tied. */}
      {errorMessage !== undefined && (
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
  );
}
