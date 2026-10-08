import { Search, X } from "lucide-react";
import type { FocusEvent, RefObject } from "react";
import {
  Button as AriaButton,
  Input,
  Label,
  SearchField as AriaSearchField,
} from "react-aria-components";

import { cn } from "@/lib/cn";

interface SearchFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  /** For a caller that must focus the field imperatively, such as a popover
   *  that opens with the field already focused. */
  inputRef?: RefObject<HTMLInputElement | null>;
  /** For a caller that wants to react to the field regaining focus, such as
   *  selecting the current text so typing replaces it rather than appending. */
  onFocus?: (event: FocusEvent<HTMLInputElement>) => void;
}

export function SearchField({
  label,
  value,
  onChange,
  placeholder,
  className,
  inputRef,
  onFocus,
}: Readonly<SearchFieldProps>) {
  return (
    <AriaSearchField
      value={value}
      onChange={onChange}
      className={cn("flex flex-col", className)}
    >
      <Label className="sr-only">{label}</Label>
      <div className="relative">
        <span
          aria-hidden
          className="text-muted-foreground pointer-events-none absolute inset-y-0 left-3 flex items-center"
        >
          <Search size={16} />
        </span>
        <Input
          ref={inputRef}
          {...(placeholder !== undefined ? { placeholder } : {})}
          {...(onFocus !== undefined ? { onFocus } : {})}
          className={cn(
            "border-border bg-surface text-foreground placeholder:text-muted-foreground focus-visible:ring-focus-ring w-full rounded-md border py-2 pl-9 text-sm outline-none focus-visible:ring-2 [&::-webkit-search-cancel-button]:appearance-none",
            value === "" ? "pr-3" : "pr-9",
          )}
        />
        {value !== "" && (
          // SearchField's context names this button and wires it to clear the value.
          <AriaButton className="text-muted-foreground hover:text-foreground absolute inset-y-0 right-1 my-auto flex size-7 cursor-pointer items-center justify-center rounded-md outline-none">
            <X size={14} aria-hidden />
          </AriaButton>
        )}
      </div>
    </AriaSearchField>
  );
}
