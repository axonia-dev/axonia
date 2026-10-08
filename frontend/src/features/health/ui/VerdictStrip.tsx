import type { Tone, Verdict } from "@/features/health/domain/verdict";

const TONE_COLOR: Record<Tone, string> = {
  danger: "var(--danger)",
  warning: "var(--warning)",
  success: "var(--success)",
  neutral: "var(--muted-foreground)",
};

/**
 * Three words and a jump link per chapter. Deliberately not sentences: sentences are what the
 * deleted diagnosis panel did, and repeating them here would repeat its mistake.
 */
export function VerdictStrip({ verdicts }: Readonly<{ verdicts: Verdict[] }>) {
  return (
    <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
      {verdicts.map((verdict) => (
        <a
          key={verdict.chapter}
          href={`#${verdict.chapter}`}
          className="border-border bg-surface hover:border-border-strong relative block overflow-hidden rounded-md border p-4 transition"
        >
          <span
            aria-hidden
            className="absolute inset-y-0 left-0 w-[3px]"
            style={{ background: TONE_COLOR[verdict.tone] }}
          />
          <span className="text-muted-foreground flex items-center gap-2 text-[10.5px] font-bold tracking-widest uppercase">
            <span
              aria-hidden
              className="size-[7px] shrink-0 rounded-full"
              style={{ background: TONE_COLOR[verdict.tone] }}
            />
            {verdict.label}
          </span>
          <span className="mt-1 block text-[19px] font-semibold">
            {verdict.state}
          </span>
          <span className="text-muted-foreground block text-[12.5px]">
            {verdict.note}
          </span>
        </a>
      ))}
    </div>
  );
}
