import { cn } from "@/lib/cn";

const TONE = { "data-1": "bg-data-1", "data-2": "bg-data-2" } as const;

/**
 * One segment per share, widest first, the first `strong` of them in the strong tone and the rest
 * off. No label, no title, no focus: position is the only identity a segment has, which is the
 * aggregates-only rule expressed as a rendering constraint rather than a promise.
 */
export function StageBar({
  name,
  shares,
  strong,
  tone,
  thin = false,
}: Readonly<{
  name: string;
  shares: number[];
  strong: number;
  tone: keyof typeof TONE;
  thin?: boolean;
}>) {
  return (
    <div
      aria-hidden="true"
      data-stage-bar={name}
      className={cn(
        "mt-2.5 mb-2 flex gap-0.5 overflow-hidden",
        thin ? "h-2 rounded-sm" : "h-3.5 rounded-[7px]",
      )}
    >
      {shares.map((share, index) => (
        <div
          key={index}
          style={{ flexGrow: share, flexBasis: 0 }}
          className={cn(
            "h-full",
            index < strong ? TONE[tone] : "bg-border-strong",
          )}
        />
      ))}
    </div>
  );
}
