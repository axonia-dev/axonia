import { ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface SectionRowProps {
  to: string;
  leading: ReactNode;
  title: string;
  subtitle?: ReactNode | undefined;
  meta?: ReactNode | undefined;
}

/**
 * One row inside a SectionCard: a tile, a title over an optional subtitle, an optional
 * trailing count, and the chevron. The chevron is drawn in every row, meta or not, so
 * the trailing edge lands in the same place in every card on the page.
 */
export function SectionRow({
  to,
  leading,
  title,
  subtitle,
  meta,
}: Readonly<SectionRowProps>) {
  return (
    <Link
      to={to}
      className="group hover:bg-surface-sunken flex items-center gap-3 rounded-md px-2 py-2.5 transition-colors"
    >
      <span className="shrink-0">{leading}</span>
      <div className="min-w-0 flex-1">
        <p className="text-foreground truncate font-medium">{title}</p>
        {subtitle && (
          <div className="text-muted-foreground flex items-center gap-1.5 overflow-hidden text-sm">
            {subtitle}
          </div>
        )}
      </div>
      {meta && <span className="shrink-0">{meta}</span>}
      <ChevronRight
        size={16}
        aria-hidden
        className="text-muted-foreground shrink-0 opacity-0 transition-opacity group-hover:opacity-100"
      />
    </Link>
  );
}
