import type { ReleasePreviewCopy } from "@/features/events/ui/ReleasePreviewDialog";

/**
 * What each releasable message's preview is called, in one place.
 *
 * A session states its release once, as the chip in its facts strip, and offers one preview from
 * it. The cards that also mention a release read their copy from here rather than each keeping a
 * constant of its own, so a message type can never be previewed under two different names.
 */
export const WRAP_UP_PREVIEW: ReleasePreviewCopy = {
  messageType: "session_wrap_up",
  dialogTitle: "Wrap-up preview",
  iframeTitle: "Wrap-up email preview",
};

export const REMINDER_PREVIEW: ReleasePreviewCopy = {
  messageType: "session_upcoming",
  dialogTitle: "Reminder preview",
  iframeTitle: "Reminder email preview",
};
