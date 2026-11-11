import { MessageCircle } from "lucide-react";
import type { ReactNode, Ref } from "react";

interface OpenRingProps {
  dot: boolean;
  children: ReactNode;
  ref?: Ref<HTMLElement> | undefined;
}

/** The sign of a person open to questions: a slowly turning dashed ring around the face, which
 *  sits inset, and a speech dot at the bottom right. The dash already means "declared, not
 *  proven" on the tier badge and the map. */
export function OpenRing({ dot, children, ref }: Readonly<OpenRingProps>) {
  return (
    <span
      ref={ref}
      data-open="true"
      className="relative inline-flex shrink-0 rounded-full"
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 42 42"
        className="animate-open-ring pointer-events-none absolute -inset-px size-[calc(100%+2px)]"
      >
        <circle
          cx="21"
          cy="21"
          r="19.5"
          fill="none"
          stroke="var(--focus-ring)"
          strokeWidth="1.6"
          strokeDasharray="4 3.2"
          strokeLinecap="round"
        />
      </svg>
      <span className="inline-flex scale-80 rounded-full">{children}</span>
      {dot && (
        <span
          data-testid="open-dot"
          aria-hidden="true"
          className="bg-focus-ring text-primary-foreground ring-surface absolute -right-0.75 -bottom-0.75 grid size-[42%] place-items-center rounded-full ring-2"
        >
          <MessageCircle className="size-[60%]" strokeWidth={3} />
        </span>
      )}
    </span>
  );
}
