import type { ReactNode } from "react";

import { FormError } from "@/components/patterns/FormError";
import type { ButtonProps } from "@/components/ui/Button";
import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";

interface Props {
  /** Accessible name of the confirmation, e.g. "Confirm delete". */
  label: string;
  isOpen: boolean;
  /** What is being confirmed, in the caller's own words. */
  children: ReactNode;
  confirmLabel: string;
  /** `danger` is reserved for a removal. A settling decision that stays on the record is not
   *  destructive and must not borrow the colour that says it is. */
  confirmVariant?: ButtonProps["variant"];
  isPending?: boolean;
  error?: unknown;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * The small anchored confirmation a row asks for before an action it cannot take back silently.
 * Anchored to the nearest positioned ancestor, so the row that hosts it carries `relative`.
 */
export function ConfirmPopover({
  label,
  isOpen,
  children,
  confirmLabel,
  confirmVariant = "danger",
  isPending = false,
  error,
  onConfirm,
  onCancel,
}: Readonly<Props>) {
  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-label={label}
      className="border-border bg-surface absolute top-10 right-2 z-20 w-56 rounded-[var(--radius-md)] border p-3 shadow-[var(--shadow-lg)]"
    >
      <div className="text-foreground mb-3 text-sm">{children}</div>
      <FormError error={error} className="mb-2 text-xs" />
      <DialogFooter>
        <Button variant="secondary" size="sm" onPress={onCancel}>
          Cancel
        </Button>
        <Button
          variant={confirmVariant}
          size="sm"
          isDisabled={isPending}
          onPress={onConfirm}
        >
          {confirmLabel}
        </Button>
      </DialogFooter>
    </div>
  );
}
