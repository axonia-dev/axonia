import type { ReactNode } from "react";
import {
  Dialog as AriaDialog,
  Heading,
  Modal,
  ModalOverlay,
} from "react-aria-components";

import { cn } from "@/lib/cn";

type DialogSize = "md" | "2xl" | "takeover";

interface DialogProps {
  /** Whether the dialog is shown. Controlled by the caller. */
  isOpen: boolean;
  /** Called with `false` when the dialog requests to close (Escape, backdrop). */
  onOpenChange: (isOpen: boolean) => void;
  /** Heading shown at the top of the panel; also the accessible name (computed from
   * its rendered text, so a `ReactNode` title composed of plain text still names the
   * dialog correctly for assistive tech). */
  title: ReactNode;
  /** Use "alertdialog" for destructive confirmations that demand a choice. */
  role?: "dialog" | "alertdialog";
  /** Widest the panel is allowed to grow on `sm:` and up. `"md"` (the default) fits a
   *  form or a confirmation; `"2xl"` is for content with its own intrinsic width;
   *  `"takeover"` fills the viewport, for content that is a whole document in its own
   *  right - a rendered email read at the size it will actually arrive. */
  size?: DialogSize;
  /**
   * Drops the panel's bottom padding, for a body whose last child is a pinned `DialogFooter`.
   *
   * Not cosmetic. A sticky element is clamped to its containing block, and with the padding in
   * place that block ends where the padding starts - so a pinned footer settles a padding's height
   * above the foot of the panel and the content it is meant to cover scrolls through the gap
   * beneath it. The footer puts the same padding back inside itself, where its own background
   * covers it.
   */
  pinnedFooter?: boolean;
  /**
   * Panel body. A render-prop form receives `close` to dismiss programmatically
   * (e.g. a Cancel button) without the caller threading its own handler.
   */
  children: ReactNode | ((opts: { close: () => void }) => ReactNode);
}

const overlayClass =
  "fixed inset-0 z-50 flex items-end justify-center bg-black/40 backdrop-blur-sm sm:items-center sm:p-4 data-[entering]:opacity-0 data-[exiting]:opacity-0 motion-safe:transition-opacity";

const sizeClass: Record<DialogSize, string> = {
  md: "sm:max-w-md",
  "2xl": "sm:max-w-2xl",
  takeover: "sm:max-w-[68rem]",
};

/** Only the takeover claims a height of its own. Every other dialog is as tall as its
 *  content up to a cap, which is what keeps a two-line confirmation two lines tall. */
const heightClass: Record<DialogSize, string> = {
  md: "max-h-[85vh]",
  "2xl": "max-h-[85vh]",
  takeover: "h-[92vh]",
};

const modalClass =
  "w-full data-[entering]:translate-y-4 data-[exiting]:translate-y-4 sm:data-[entering]:translate-y-0 sm:data-[exiting]:translate-y-0 sm:data-[entering]:scale-95 sm:data-[exiting]:scale-95 motion-safe:transition-transform";

const dialogClass =
  "border-border-strong bg-surface relative flex flex-col gap-4 overflow-y-auto rounded-t-lg border p-6 shadow-lg outline-none sm:rounded-lg";

export function Dialog({
  isOpen,
  onOpenChange,
  title,
  role = "dialog",
  size = "md",
  pinnedFooter = false,
  children,
}: Readonly<DialogProps>) {
  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      isDismissable={role !== "alertdialog"}
      className={overlayClass}
    >
      <Modal className={cn(modalClass, sizeClass[size])}>
        <AriaDialog
          role={role}
          className={cn(dialogClass, heightClass[size], pinnedFooter && "pb-0")}
        >
          {(renderProps) => (
            <>
              <Heading
                slot="title"
                className="font-display text-foreground text-lg font-semibold"
              >
                {title}
              </Heading>
              {typeof children === "function"
                ? children(renderProps)
                : children}
            </>
          )}
        </AriaDialog>
      </Modal>
    </ModalOverlay>
  );
}
