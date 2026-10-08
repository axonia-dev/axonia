import { Check, Copy, CopyX } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";

type CopyStatus = "idle" | "copied" | "failed";

const statusIcon: Record<CopyStatus, typeof Copy> = {
  idle: Copy,
  copied: Check,
  failed: CopyX,
};

const statusText: Record<CopyStatus, string> = {
  idle: "Copy",
  copied: "Copied",
  failed: "Copy failed",
};

const statusAnnouncement: Record<CopyStatus, string> = {
  idle: "",
  copied: "Copied to clipboard.",
  failed: "Copy failed.",
};

const STATUS_RESET_MS = 2000;

interface Props {
  value: string;
  /** What this button copies, for a reader who cannot see what it sits beside. */
  label: string;
  /** Drops the word and leaves the glyph, for a row too narrow to carry both. The label still
   *  names the control, so nothing is lost to anybody reading it out. */
  iconOnly?: boolean;
}

/**
 * Putting a value on the clipboard, and saying whether it went.
 *
 * Its own component because two unrelated places need the same behaviour and the same three
 * outcomes: the calendar link, which is handed over once, and a session's joining details, which
 * are a passcode somebody is about to paste into a meeting client. Copied instead, the clipboard
 * guard and the reset timer would be duplicated per caller, and only one copy would ever get
 * fixed.
 */
export function CopyButton({
  value,
  label,
  iconOnly = false,
}: Readonly<Props>) {
  const [status, setStatus] = useState<CopyStatus>("idle");

  useEffect(() => {
    if (status === "idle") {
      return;
    }
    const timer = setTimeout(() => setStatus("idle"), STATUS_RESET_MS);
    return () => clearTimeout(timer);
  }, [status]);

  /**
   * The DOM types declare `navigator.clipboard` as always present, but a browser
   * withholds it outside a secure context, where reading it yields undefined.
   */
  function handleCopy() {
    const { clipboard } = navigator as Omit<Navigator, "clipboard"> & {
      clipboard?: Clipboard;
    };
    if (clipboard === undefined) {
      setStatus("failed");
      return;
    }
    void clipboard
      .writeText(value)
      .then(() => setStatus("copied"))
      .catch(() => setStatus("failed"));
  }

  const StatusIcon = statusIcon[status];

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onPress={handleCopy}
        aria-label={label}
      >
        <StatusIcon size={13} />
        {!iconOnly && statusText[status]}
      </Button>
      <span aria-live="polite" className="sr-only">
        {statusAnnouncement[status]}
      </span>
    </>
  );
}
