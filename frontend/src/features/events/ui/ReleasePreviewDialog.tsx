import { MessagePreviewDialog } from "@/components/patterns/MessagePreviewDialog";
import { useMessagePreview } from "@/features/events/api/use-session";
import type { ReleaseMessageType } from "@/features/events/domain/events-gateway";
import { eventsErrorMessage } from "@/features/events/events-errors";

/** What the dialog is called and what the iframe announces itself as, per message type. */
export interface ReleasePreviewCopy {
  messageType: ReleaseMessageType;
  dialogTitle: string;
  iframeTitle: string;
}

interface Props {
  slug: string;
  sessionId: number;
  copy: ReleasePreviewCopy;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Renders exactly what a send of `copy.messageType` would produce. All this
 * adds to {@link MessagePreviewDialog} is the session-scoped query and this feature's own
 * translation of a failed render; the sandboxing and the frame are shared.
 */
export function ReleasePreviewDialog({
  slug,
  sessionId,
  copy,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  const preview = useMessagePreview(slug, sessionId, copy.messageType, isOpen);

  return (
    <MessagePreviewDialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      dialogTitle={copy.dialogTitle}
      iframeTitle={copy.iframeTitle}
      preview={preview}
      errorMessage={eventsErrorMessage(preview.error)}
    />
  );
}
