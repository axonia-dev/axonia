import type { ReactNode } from "react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";

type Floater = { key: number; x: string; size: string; dur: number };

/** Animated love button: floating-hearts on hover, burst on click, delayed mutation. */
export function AnimatedLoveButton({
  btn,
  ariaLabel,
  isPending,
  onPress,
  children,
}: Readonly<{
  btn: string;
  ariaLabel?: string;
  isPending: boolean;
  onPress: () => void;
  children: ReactNode;
}>) {
  const [floaters, setFloaters] = useState<Floater[]>([]);
  const floatTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const nextKey = useRef(0);

  useEffect(() => {
    return () => {
      if (floatTimerRef.current) clearInterval(floatTimerRef.current);
    };
  }, []);

  function spawnFloater() {
    setFloaters((prev) => [
      ...prev,
      {
        key: nextKey.current++,
        // eslint-disable-next-line sonarjs/pseudo-random
        x: `${10 + Math.random() * 60}%`,
        // eslint-disable-next-line sonarjs/pseudo-random
        size: `${8 + Math.random() * 6}px`,
        // eslint-disable-next-line sonarjs/pseudo-random
        dur: 700 + Math.random() * 400,
      },
    ]);
  }

  function startFloating() {
    spawnFloater();
    floatTimerRef.current = setInterval(spawnFloater, 320);
  }

  function stopFloating() {
    if (floatTimerRef.current) clearInterval(floatTimerRef.current);
    floatTimerRef.current = null;
  }

  function removeFloater(key: number) {
    setFloaters((prev) => prev.filter((f) => f.key !== key));
  }

  return (
    <div
      className="relative"
      onMouseEnter={startFloating}
      onMouseLeave={stopFloating}
    >
      <Button
        variant="ghost"
        size="sm"
        className={btn}
        {...(ariaLabel !== undefined ? { "aria-label": ariaLabel } : {})}
        isDisabled={isPending}
        onPress={onPress}
      >
        {children}
      </Button>
      {floaters.map((f) => (
        <span
          key={f.key}
          aria-hidden
          className="text-primary pointer-events-none absolute"
          style={{
            left: f.x,
            bottom: "28px",
            fontSize: f.size,
            animation: `float-up ${f.dur}ms ease-out forwards`,
          }}
          onAnimationEnd={() => removeFloater(f.key)}
        >
          ♥
        </span>
      ))}
    </div>
  );
}
