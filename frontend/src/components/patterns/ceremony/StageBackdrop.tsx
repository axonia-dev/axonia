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
 * chunk has loaded; the stage inside only fades it out as it settles.
 */
export function StageBackdrop({ children }: Readonly<{ children: ReactNode }>) {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const backdrop = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    backdrop.current?.animate?.([{ opacity: 0 }, { opacity: 1 }], ENTRANCE);
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
          className="fixed inset-0 z-[100] bg-[#0d0910]"
        />,
        document.body,
      )}
      {children}
    </BackdropContext>
  );
}
