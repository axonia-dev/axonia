import { type ReactNode, useId } from "react";

/** A one-time question put to the member, answered by the buttons beside it. */
interface AskProps {
  title: string;
  lines: string[];
  /** A line under the buttons. */
  note?: string;
  children: ReactNode;
}

export function Ask({ title, lines, note, children }: Readonly<AskProps>) {
  const titleId = useId();
  return (
    <section
      aria-labelledby={titleId}
      className="border-border-strong bg-surface flex flex-wrap items-center gap-3 rounded-2xl border px-4 py-3"
    >
      <div className="flex min-w-48 flex-1 flex-col gap-0.5">
        <h2
          id={titleId}
          className="text-foreground text-sm font-semibold wrap-anywhere"
        >
          {title}
        </h2>
        {lines.map((line) => (
          <p key={line} className="text-muted-foreground text-xs wrap-anywhere">
            {line}
          </p>
        ))}
      </div>
      <div className="ml-auto flex flex-wrap gap-2">{children}</div>
      {note !== undefined && (
        <p className="text-muted-foreground w-full text-right text-xs">
          {note}
        </p>
      )}
    </section>
  );
}
