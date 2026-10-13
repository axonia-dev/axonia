import { type ComponentType, lazy, useEffect } from "react";

import type { HeroCeremonyProps } from "@/features/community/ui/ceremony/HeroCeremony";
import type { WelcomePreviewProps } from "@/features/community/ui/ceremony/WelcomePreview";

type Loaded<P> = Promise<{ default: ComponentType<P> }>;

/** What a failed chunk load degrades to: no ceremony, the page's own card stays. */
function Skip({ onDone }: Readonly<{ onDone: () => void }>) {
  useEffect(() => {
    onDone();
  }, [onDone]);
  return null;
}

function loadHeroCeremony(): Loaded<HeroCeremonyProps> {
  return import("@/features/community/ui/ceremony/HeroCeremony").then(
    (module) => ({ default: module.HeroCeremony }),
    () => ({ default: Skip }),
  );
}

function loadWelcomePreview(): Loaded<WelcomePreviewProps> {
  return import("@/features/community/ui/ceremony/WelcomePreview").then(
    (module) => ({ default: module.WelcomePreview }),
    () => ({ default: Skip }),
  );
}

export const LazyHeroCeremony = lazy(loadHeroCeremony);
export const LazyWelcomePreview = lazy(loadWelcomePreview);

export function preloadHeroCeremony(): void {
  void loadHeroCeremony();
}

export function preloadWelcomePreview(): void {
  void loadWelcomePreview();
}
