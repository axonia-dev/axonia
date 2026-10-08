/** The small day/month block a past list leads each row with. */
export function DateTile({ iso }: Readonly<{ iso: string }>) {
  const date = new Date(iso);
  return (
    <div className="border-border bg-surface-sunken min-w-[44px] rounded-lg border px-1.5 py-1 text-center">
      <p className="text-foreground text-base leading-tight font-extrabold tabular-nums">
        {date.getDate()}
      </p>
      <p className="text-muted-foreground text-[9px] font-semibold tracking-wide uppercase">
        {date.toLocaleDateString(undefined, { month: "short" })}
      </p>
    </div>
  );
}
