import { TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

interface DangerAlertProps {
  children: ReactNode;
}

/**
 * Shared `role="alert"` chrome: the tinted border, the triangle glyph, the
 * stacked-text layout. Content is entirely the caller's - today that's only
 * `ChatChannelWarning`'s overview-page notice, kept as its own component so a
 * second danger notice elsewhere in the community UI can reuse the same
 * visual danger box without copying it.
 */
export function DangerAlert({ children }: Readonly<DangerAlertProps>) {
  return (
    <div
      role="alert"
      className="border-danger/40 bg-danger/10 text-danger flex items-start gap-2 rounded-md border p-3 text-sm"
    >
      <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col gap-1">{children}</div>
    </div>
  );
}
