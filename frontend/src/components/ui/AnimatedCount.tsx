import { cn } from "@/lib/cn";

interface AnimatedCountProps {
  value: number;
  className?: string;
}

export function AnimatedCount({
  value,
  className,
}: Readonly<AnimatedCountProps>) {
  return (
    <span className={cn("inline-block tabular-nums", className)}>
      <span
        key={value}
        className="inline-block motion-safe:animate-[count-pop_280ms_var(--ease-standard,ease-out)]"
      >
        {value}
      </span>
    </span>
  );
}
