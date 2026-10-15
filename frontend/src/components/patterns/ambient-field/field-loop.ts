import type { CeremonyClock } from "@/components/patterns/ceremony/clock";

export interface Loop {
  pause(): void;
  resume(): void;
}

const SLOW_FRAME_MS = 50;
const SLOW_FRAMES_TO_STOP = 5;
/** A page still rendering its panels drops frames the field is not to blame for. */
const WARM_UP_MS = 1000;

/** Runs `draw` every frame until paused, or for good once frames stay slow after a warm-up. */
export function runLoop(
  draw: (seconds: number) => void,
  clock: CeremonyClock,
): Loop {
  let handle: number | null = null;
  let last = 0;
  let warmUntil = 0;
  let slow = 0;
  let stopped = false;
  const tick = () => {
    const now = clock.now();
    slow = now > warmUntil && now - last > SLOW_FRAME_MS ? slow + 1 : 0;
    last = now;
    draw(now / 1000);
    stopped = slow >= SLOW_FRAMES_TO_STOP;
    handle = stopped ? null : clock.request(tick);
  };
  const pause = () => {
    if (handle !== null) clock.cancel(handle);
    handle = null;
  };
  const resume = () => {
    if (handle !== null || stopped) return;
    last = clock.now();
    warmUntil = last + WARM_UP_MS;
    handle = clock.request(tick);
  };
  return { pause, resume };
}

/** Animates only while the tab is shown and the field is on screen; starts at once if both hold. */
export function watchVisibility(target: Element, loop: Loop): () => void {
  let onScreen = true;
  let tabVisible = document.visibilityState !== "hidden";
  const apply = () => (onScreen && tabVisible ? loop.resume() : loop.pause());
  const onVisibility = () => {
    tabVisible = document.visibilityState !== "hidden";
    apply();
  };
  // A busy main thread can batch several crossings, oldest first: the last one is now.
  const observer = new IntersectionObserver((entries) => {
    onScreen = entries.at(-1)?.isIntersecting ?? onScreen;
    apply();
  });
  observer.observe(target);
  document.addEventListener("visibilitychange", onVisibility);
  apply();
  return () => {
    loop.pause();
    observer.disconnect();
    document.removeEventListener("visibilitychange", onVisibility);
  };
}
