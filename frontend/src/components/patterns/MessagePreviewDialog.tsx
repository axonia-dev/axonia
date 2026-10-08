import { ExternalLink } from "lucide-react";
import { useState } from "react";

import { openMessageInNewTab } from "@/components/patterns/open-message-in-tab";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { cn } from "@/lib/cn";

/** The shape every message preview query answers with, whatever endpoint produced it. */
interface RenderedMessage {
  subject: string;
  html: string;
}

/** Structurally what a TanStack query result already is, so a caller passes its query
 *  straight through rather than unpacking it into three props. */
interface PreviewState {
  data: RenderedMessage | undefined;
  isLoading: boolean;
  isError: boolean;
}

interface MessagePreviewDialogProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  /** Names the dialog for the reader and for assistive tech. */
  dialogTitle: string;
  /** Names the iframe, which is otherwise an unlabelled document to a screen reader. */
  iframeTitle: string;
  preview: PreviewState;
  /** Caller-owned copy: each feature translates its own failures. */
  errorMessage: string;
}

/** 680px clears the 600px card plus its canvas gutters; 400px is a common phone
 *  viewport. Both are frame widths, never a re-render: the same `srcDoc` is measured
 *  against a narrower box, exactly as a mail client would. */
const FRAME_WIDTH = {
  desktop: "max-w-[680px]",
  phone: "max-w-[400px]",
} as const;

type PreviewWidth = keyof typeof FRAME_WIDTH;

const WIDTHS = [
  { id: "desktop", label: "Desktop" },
  { id: "phone", label: "Phone" },
] as const;

/**
 * Renders exactly what a send would produce, for any message a member could receive. The
 * body is a sandboxed `<iframe srcDoc>` with an empty `sandbox` attribute - the strictest
 * setting, disabling scripts, forms, popups and top navigation - so mail-targeted CSS and
 * markup (inline styles, `<style>` blocks, table layouts) can never leak into the app's own
 * stylesheet, and nothing the rendered HTML contains can act on the app's page or origin.
 *
 * It takes the whole viewport rather than sitting in a 672px panel. A message is a document
 * in its own right, and judging one through a window shorter than its own header is how a
 * layout fault reaches an inbox unnoticed. The width toggle is there for the same reason:
 * mail renders differently at phone and desktop widths, and both are worth a look before
 * deciding to mail an entire organization - it hides below `sm:`, where the viewport already
 * is the phone width and the dialog is a bottom sheet, so the control would only ever steal
 * a row for a choice already made.
 *
 * "Open in new tab" hands the same HTML to a full browser tab for real browser zoom and print,
 * via {@link openMessageInNewTab} - a wrapper document we author, never the mail HTML itself as
 * a top-level `blob:` navigation, which would otherwise inherit the app's own origin.
 *
 * Shared by the session release previews and the organization roundup: the sandboxing, the
 * announced loading and error states and the iframe's frame are one implementation, so a
 * third preview inherits them instead of copying them.
 */
export function MessagePreviewDialog({
  isOpen,
  onOpenChange,
  dialogTitle,
  iframeTitle,
  preview,
  errorMessage,
}: Readonly<MessagePreviewDialogProps>) {
  const [width, setWidth] = useState<PreviewWidth>("desktop");
  const { data } = preview;

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={dialogTitle}
      size="takeover"
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3">
        {preview.isLoading && (
          <div className="grid flex-1 place-items-center">
            <p role="status" className="text-muted-foreground text-sm">
              Loading preview…
            </p>
          </div>
        )}
        {preview.isError && (
          <div className="grid flex-1 place-items-center">
            <p role="alert" className="text-danger text-sm">
              {errorMessage}
            </p>
          </div>
        )}
        {data && (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-foreground text-sm font-medium">
                {data.subject}
              </p>
              <div className="flex items-center gap-2">
                <SegmentedControl
                  label="Preview width"
                  hideLabel
                  value={width}
                  options={WIDTHS}
                  onChange={(value) =>
                    setWidth(value === "phone" ? "phone" : "desktop")
                  }
                  className="hidden sm:flex"
                />
                <Button
                  variant="secondary"
                  size="sm"
                  onPress={() => openMessageInNewTab(data.subject, data.html)}
                >
                  <ExternalLink size={14} aria-hidden />
                  Open in new tab
                </Button>
              </div>
            </div>
            <div
              className={cn(
                "mx-auto min-h-0 w-full flex-1",
                FRAME_WIDTH[width],
              )}
            >
              <iframe
                title={iframeTitle}
                srcDoc={data.html}
                sandbox=""
                className="border-border h-full w-full rounded-md border bg-white"
              />
            </div>
          </>
        )}
      </div>
    </Dialog>
  );
}
