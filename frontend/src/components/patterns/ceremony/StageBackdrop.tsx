import { createPortal } from "react-dom";

import { useMediaQuery } from "@/lib/use-media-query";

/** The stage's dark backdrop alone, held up while the ceremony still loads so it starts at once. */
export function StageBackdrop() {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  return reduced
    ? null
    : createPortal(
        <div
          data-ceremony-backdrop
          aria-hidden
          className="fixed inset-0 z-[100] bg-[#0d0910]"
        />,
        document.body,
      );
}
