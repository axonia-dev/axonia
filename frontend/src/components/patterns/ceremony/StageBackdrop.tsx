import {
  createContext,
  type ReactNode,
  type RefObject,
  useContext,
  useLayoutEffect,
  useRef,
} from "react";
import { createPortal } from "react-dom";

import { useMediaQuery } from "@/lib/use-media-query";

import { STAGE_DARK, takeCurtain } from "./curtain";

const ENTRANCE = { duration: 180, easing: "ease-out" };

const BackdropContext = createContext<RefObject<HTMLElement | null> | null>(
  null,
);

/** The backdrop the stage stands on, or `null` outside a `StageBackdrop`. */
export function useStageBackdrop(): RefObject<HTMLElement | null> | null {
  return useContext(BackdropContext);
}

/**
 * The stage's dark backdrop, held from the moment a ceremony starts until it ends. Render it around
 * the ceremony and outside any Suspense boundary, so it darkens once whether or not the ceremony's
 * chunk has loaded; the stage inside only fades it out as it settles. An arrival curtain still
 * down is taken over where it stands, so the dark never dips.
 */
export function StageBackdrop({ children }: Readonly<{ children: ReactNode }>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const backdrop = useRef<HTMLDivElement>(null);
  const covered = useRef<number | null>(null);

  useLayoutEffect(() => {
    covered.current ??= takeCurtain();
    if (covered.current < 1) {
      backdrop.current?.animate?.(
        [{ opacity: covered.current }, { opacity: 1 }],
        ENTRANCE,
      );
    }
  }, [reduced]);

  if (reduced) {
    return <>{children}</>;
  }
  return (
    <BackdropContext value={backdrop}>
      {createPortal(
        <div
          ref={backdrop}
          data-ceremony-backdrop
          aria-hidden
          className="fixed inset-0 z-[100]"
          style={{ background: STAGE_DARK }}
        />,
        document.body,
      )}
      {children}
    </BackdropContext>
  );
}
