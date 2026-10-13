import { type ComponentType, lazy, useEffect, useState } from "react";

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

interface Preloadable<P> {
  readonly Component: ComponentType<P>;
  readonly preload: () => void;
}

/**
 * A lazily loaded component that renders at once, without suspending, once a preload has landed.
 * `React.lazy` suspends on its first render even when its chunk is already in, so a preloaded
 * ceremony would otherwise still flash its fallback. A failed load is not kept, so a later
 * preload tries again.
 */
function preloadable<P extends { onDone: () => void }>(
  load: () => Promise<ComponentType<P>>,
): Preloadable<P> {
  let pending: Loaded<P> | null = null;
  let ready: ComponentType<P> | null = null;
  const loadOnce = (): Loaded<P> => {
    pending ??= load().then(
      (component) => {
        ready = component;
        return { default: component };
      },
      () => {
        pending = null;
        return { default: Skip };
      },
    );
    return pending;
  };
  const Lazy = lazy(loadOnce);
  function Component(props: P) {
    const [Loaded] = useState<ComponentType<P>>(() => ready ?? Lazy);
    return <Loaded {...props} />;
  }
  return {
    Component,
    preload: () => {
      void loadOnce();
    },
  };
}

const heroCeremony = preloadable<HeroCeremonyProps>(() =>
  import("@/features/community/ui/ceremony/HeroCeremony").then(
    (module) => module.HeroCeremony,
  ),
);

const welcomePreview = preloadable<WelcomePreviewProps>(() =>
  import("@/features/community/ui/ceremony/WelcomePreview").then(
    (module) => module.WelcomePreview,
  ),
);

export const LazyHeroCeremony = heroCeremony.Component;
export const LazyWelcomePreview = welcomePreview.Component;
export const preloadHeroCeremony = heroCeremony.preload;
export const preloadWelcomePreview = welcomePreview.preload;
