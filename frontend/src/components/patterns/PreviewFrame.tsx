import type { ReactNode } from "react";

import { ThemeToggle } from "@/components/patterns/ThemeToggle";
import { Logo } from "@/components/ui/Logo";

interface PreviewFrameProps {
  children: ReactNode;
  /**
   * A top-level route has no ancestor that already sizes it, so it defaults to `min-h-dvh`: a
   * floor against the real viewport that still lets a tall page grow past it. A screen mounted
   * inside an already viewport-sized box (the offline-organization screen, under a banner that
   * has already spent part of that viewport) instead needs to exactly fill what its ancestor
   * carved out, or the floor itself becomes the overflow.
   */
  fill?: boolean;
}

/** The chrome every page outside the app's shells shares: a top bar with the logo and the theme
 *  toggle, over a narrow column. */
export function PreviewFrame({
  children,
  fill = false,
}: Readonly<PreviewFrameProps>) {
  return (
    <div className={`bg-background ${fill ? "h-full" : "min-h-dvh"}`}>
      <div className="border-border flex items-center justify-between border-b px-6 py-3">
        <Logo />
        <ThemeToggle />
      </div>
      <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-6 py-10">
        {children}
      </main>
    </div>
  );
}
