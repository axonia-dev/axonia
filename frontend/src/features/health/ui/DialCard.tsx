import type { ReactNode } from "react";

export function DialCard({
  title,
  value,
  chip,
  read,
  sparkline,
}: Readonly<{
  title: string;
  value: string;
  chip?: ReactNode;
  /**
   * A node, not a string: a dial whose trend compares something other than its own value carries
   * the chip down here beside the figure it does compare, rather than beside the value it does not.
   */
  read: ReactNode;
  sparkline?: ReactNode;
}>) {
  return (
    <article className="border-border bg-surface flex flex-col gap-2 rounded-md border p-4">
      <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
        {title}
      </h3>
      <div className="flex items-baseline gap-2">
        <span className="text-foreground text-xl font-semibold">{value}</span>
        {chip}
      </div>
      <p className="text-muted-foreground text-sm">{read}</p>
      {sparkline}
    </article>
  );
}
