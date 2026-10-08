import { TriangleAlert } from "lucide-react";

import { useMailConfigured } from "@/selfhosted/features/mail-server/api/use-mail-server";

const NOTICE =
  "border-warning/40 bg-warning/10 text-warning flex items-center gap-2 rounded-md border px-3 py-2 text-sm";

/**
 * "Mail not configured.", in the card's place for a viewer who cannot read the mail server, and
 * only while it is true: nothing while mail leaves, and nothing when the answer could not be read,
 * since a page that cannot say is no place to guess. Plain text: the card is on this page, and its
 * viewer cannot open it.
 */
export function MailNotConfiguredNotice() {
  const status = useMailConfigured();
  if (status.data !== false) return null;
  return (
    <p role="note" className={NOTICE}>
      <TriangleAlert aria-hidden size={16} className="shrink-0" />
      Mail not configured.
    </p>
  );
}
