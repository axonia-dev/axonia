import { Fragment } from "react";
import { Link } from "react-router-dom";

import { cn } from "@/lib/cn";

export interface Crumb {
  label: string;
  to?: string;
}

interface BreadcrumbProps {
  items: ReadonlyArray<Crumb>;
  className?: string;
}

export function Breadcrumb({ items, className }: Readonly<BreadcrumbProps>) {
  return (
    <nav
      aria-label="Breadcrumb"
      className={cn(
        "text-muted-foreground flex items-center gap-2 text-sm",
        className,
      )}
    >
      {items.map((item, index) => {
        const isLast = index === items.length - 1;
        return (
          <Fragment key={`${index}-${item.label}`}>
            {item.to && !isLast ? (
              <Link
                to={item.to}
                className="hover:text-foreground transition-colors"
              >
                {item.label}
              </Link>
            ) : (
              <span
                className={cn(isLast && "text-foreground font-semibold")}
                aria-current={isLast ? "page" : undefined}
              >
                {item.label}
              </span>
            )}
            {!isLast && (
              <span aria-hidden className="opacity-50">
                ›
              </span>
            )}
          </Fragment>
        );
      })}
    </nav>
  );
}
