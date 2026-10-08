import { Check } from "lucide-react";
import type { CSSProperties } from "react";

import { chipClass } from "./OnboardingChip";

const ANGLES = [18, 54, 90, 126, 162, 198, 234, 270, 306, 342];
const DISTANCE = 54;

/** Sparks, not hearts: the burst of hearts is this product's love gesture and means something
 *  else. Rides the shared `slot-spark` keyframe so the motion is the one already in use. */
function FinishBurst() {
  return (
    <span aria-hidden className="pointer-events-none absolute inset-0">
      {ANGLES.map((degrees, index) => {
        const radians = (degrees * Math.PI) / 180;
        return (
          <span
            key={degrees}
            className="bg-success absolute top-1/2 left-1/2 size-1.5 rounded-full motion-safe:animate-[slot-spark_0.75s_ease-out_forwards]"
            style={
              {
                "--tx": `${Math.cos(radians) * DISTANCE}px`,
                "--ty": `${Math.sin(radians) * DISTANCE}px`,
                animationDelay: `${index * 18}ms`,
              } as CSSProperties
            }
          />
        );
      })}
    </span>
  );
}

/**
 * What the last tick looks like. The ring the chip has been filling closes, turns green and loses
 * its count, the word becomes the result, and the sparks go off once.
 *
 * Not a button and not a popover: there is nothing left to open, and nothing to dismiss.
 * {@link OnboardingAffordance} unmounts it a few seconds later, which is the whole lifetime of
 * this component - it holds no state and no timer of its own.
 */
export function OnboardingFinishChip() {
  return (
    <span
      role="status"
      className={`${chipClass} border-success/40 bg-success/10 text-success relative cursor-default`}
    >
      <span
        aria-hidden
        className="bg-success text-success-foreground grid size-[22px] shrink-0 place-items-center rounded-full motion-safe:animate-[avatar-pop_0.45s_cubic-bezier(0.34,1.56,0.64,1)]"
      >
        <Check size={12} strokeWidth={3.4} />
      </span>
      Good to go!
      <FinishBurst />
    </span>
  );
}
