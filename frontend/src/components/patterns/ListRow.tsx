import type { CSSProperties, ReactNode } from "react";
import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";

interface ListRowBaseProps {
  title: ReactNode;
  meta?: string;
  leading?: ReactNode;
  trailing?: ReactNode;
  action?: ReactNode;
  accent?: string;
  className?: string;
}

type ListRowProps = ListRowBaseProps &
  ({ href: string; onPress?: never } | { onPress: () => void; href?: never });

const ROW_SHELL_CLASS = cn(
  "group border-border bg-surface relative flex w-full items-center overflow-hidden rounded-md border transition-[transform,box-shadow] duration-150 motion-safe:hover:-translate-y-0.5 hover:shadow-md",
  "before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:w-[3px] before:bg-[var(--k)] before:opacity-0 before:transition-opacity hover:before:opacity-100",
);

const ROW_CLASS = cn(
  ROW_SHELL_CLASS,
  "cursor-pointer gap-3 px-4 py-3 text-left outline-none focus-visible:ring-focus-ring focus-visible:ring-2",
);

const ACTION_ROW_CLASS = cn(
  ROW_SHELL_CLASS,
  "flex-wrap gap-x-3 gap-y-1 pr-4 has-[[data-row-link]:focus-visible]:ring-focus-ring has-[[data-row-link]:focus-visible]:ring-2",
);

const ACTION_LINK_CLASS =
  "flex min-w-0 flex-1 cursor-pointer items-center gap-3 py-3 pl-4 text-left outline-none";

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
      {trailing && <span className="ml-auto flex">{trailing}</span>}
    </>
  );
}

export function ListRow({
  title,
  meta,
  leading,
  trailing,
  action,
  accent = "var(--primary)",
  className,
  ...activation
}: Readonly<ListRowProps>) {
  const style = { "--k": accent } as CSSProperties;
  const body = (
    <Body title={title} meta={meta} leading={leading} trailing={trailing} />
  );

  if (action !== undefined) {
    return (
      <div style={style} className={cn(ACTION_ROW_CLASS, className)}>
        {"href" in activation ? (
          <Link
            to={activation.href}
            data-row-link
            className={ACTION_LINK_CLASS}
          >
            {body}
          </Link>
        ) : (
          <button
            type="button"
            onClick={activation.onPress}
            data-row-link
            className={ACTION_LINK_CLASS}
          >
            {body}
          </button>
        )}
        <div className="contents">{action}</div>
      </div>
    );
  }

  const rowClass = cn(ROW_CLASS, className);
  return "href" in activation ? (
    <Link to={activation.href} style={style} className={rowClass}>
      {body}
    </Link>
  ) : (
    <button
      type="button"
      onClick={activation.onPress}
      style={style}
      className={rowClass}
    >
      {body}
    </button>
  );
}
