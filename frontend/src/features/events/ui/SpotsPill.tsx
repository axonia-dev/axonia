export function SpotsPill({ label }: Readonly<{ label: string }>) {
  return (
    <span className="text-primary-strong border-primary/45 bg-primary/10 rounded-full border border-dashed px-2 py-0.5 text-xs font-semibold">
      {label}
    </span>
  );
}
