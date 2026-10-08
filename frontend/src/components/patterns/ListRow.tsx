import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";

interface ListRowBaseProps {
  title: ReactNode;
  meta?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  accent?: string;
  className?: string;
}

type ListRowProps = ListRowBaseProps &
  ({ href: string; onPress?: never } | { onPress: () => void; href?: never });

const ROW_CLASS = cn(
  "group border-border bg-surface relative flex w-full cursor-pointer items-center gap-3 overflow-hidden rounded-md border px-4 py-3 text-left transition-[transform,box-shadow] duration-150 outline-none",
  "before:absolute before:inset-y-0 before:left-0 before:w-[3px] before:bg-[var(--k)] before:opacity-0 before:transition-opacity hover:before:opacity-100",
  "focus-visible:ring-focus-ring motion-safe:hover:-translate-y-0.5 hover:shadow-md focus-visible:ring-2",
);

interface BodyProps {
  title: ReactNode;
  meta: string | undefined;
  leading: ReactNode | undefined;
  trailing: ReactNode | undefined;
}

function Body({ title, meta, leading, trailing }: Readonly<BodyProps>) {
  return (
    <>
      {leading}
      <span className="flex min-w-0 flex-col">
        <span className="text-foreground truncate font-medium">{title}</span>
        {meta && <span className="text-muted-foreground text-sm">{meta}</span>}
      </span>
      {trailing && <span className="ml-auto">{trailing}</span>}
    </>
  );
}

export function ListRow({
  title,
  meta,
  leading,
  trailing,
  accent = "var(--primary)",
  className,
  ...activation
}: Readonly<ListRowProps>) {
  const style = { "--k": accent } as CSSProperties;
  const body = (
    <Body title={title} meta={meta} leading={leading} trailing={trailing} />
  );

  if ("href" in activation) {
    return (
      <Link
        to={activation.href}
        style={style}
        className={cn(ROW_CLASS, className)}
      >
        {body}
      </Link>
    );
  }
  return (
    <button
      type="button"
      onClick={activation.onPress}
      style={style}
      className={cn(ROW_CLASS, className)}
    >
      {body}
    </button>
  );
}
